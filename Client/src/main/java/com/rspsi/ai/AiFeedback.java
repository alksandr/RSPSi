package com.rspsi.ai;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import javax.imageio.ImageIO;

/**
 * Read-only runtime feedback channel for an AI assistant (or human) watching the editor.
 * Writes everything under ./logs :
 *   editor.log  - tee of System.out + System.err + uncaught exceptions (fresh each run)
 *   frame.png   - latest rendered game frame (throttled)
 *   status.json - editor state snapshot on the same tick as frame.png
 *
 * ponytail: files-on-disk instead of an MCP/HTTP server. The AI already has Read+Bash,
 * so a tailable log + a snapshot PNG + a status file cover read-only feedback with zero deps.
 * Upgrade to an embedded HTTP endpoint only if the AI ever needs to *drive* the editor.
 */
public final class AiFeedback {

	// Anchored to ~/.rspsi/logs so the path is deterministic regardless of launch CWD
	// (gradle run, run-editor.bat, and the installer all have different working dirs).
	private static final File DIR = new File(new File(System.getProperty("user.home"), ".rspsi"), "logs");
	private static final long CAPTURE_INTERVAL_MS = 2000;

	private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "ai-feedback-io");
		t.setDaemon(true);
		return t;
	});

	private static volatile long lastCapture;
	private static volatile String lastError = "";

	private AiFeedback() {}

	/** Redirect stdout/stderr through a tee (console + logs/editor.log) and log uncaught exceptions. */
	public static synchronized void installConsoleTee() {
		try {
			DIR.mkdirs();
			// Fresh log per run: always the current session, never unbounded growth.
			OutputStream file = new FileOutputStream(new File(DIR, "editor.log"), false);
			System.setOut(new PrintStream(new TeeOutputStream(new FileOutputStream(java.io.FileDescriptor.out), file), true, StandardCharsets.UTF_8));
			System.setErr(new PrintStream(new TeeOutputStream(new FileOutputStream(java.io.FileDescriptor.err), file), true, StandardCharsets.UTF_8));
		} catch (IOException e) {
			// If we cannot open the log, leave stdout/stderr untouched.
			e.printStackTrace();
		}

		Thread.setDefaultUncaughtExceptionHandler((t, e) -> logUncaught(t, e));
	}

	/** Call from the JavaFX Application thread once it exists, so FX-thread crashes are captured too. */
	public static void installFxHandler() {
		Thread.currentThread().setUncaughtExceptionHandler((t, e) -> logUncaught(t, e));
	}

	private static void logUncaught(Thread t, Throwable e) {
		lastError = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
		System.err.println("[uncaught] thread=" + t.getName());
		e.printStackTrace();
	}

	public static void recordError(String msg) {
		lastError = msg;
	}

	public static String lastError() {
		return lastError;
	}

	/**
	 * Snapshot the current frame + status. Cheap and throttled: copies the pixel array on the
	 * caller (render) thread, then hands PNG/JSON encoding to a background thread.
	 *
	 * The status supplier is only invoked when a capture actually fires (every ~2s), NOT on
	 * every frame, so the caller must NOT build the JSON eagerly at the call site.
	 *
	 * @param argb pixel array in 0x__RRGGBB layout (alpha ignored)
	 */
	public static void tick(int width, int height, int[] argb, Supplier<String> statusSupplier) {
		long now = System.currentTimeMillis();
		if (now - lastCapture < CAPTURE_INTERVAL_MS || width <= 0 || height <= 0)
			return;
		lastCapture = now;

		final int[] copy = Arrays.copyOf(argb, width * height);
		IO.submit(() -> writeFrame(width, height, copy));

		String statusJson = null;
		if (statusSupplier != null) {
			try {
				statusJson = statusSupplier.get();
			} catch (Exception e) {
				// status is best-effort; never block the frame capture on it
			}
		}
		if (statusJson != null) {
			final byte[] bytes = statusJson.getBytes(StandardCharsets.UTF_8);
			IO.submit(() -> writeAtomic("status.json", bytes));
		}
	}

	private static void writeFrame(int width, int height, int[] argb) {
		try {
			BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			img.setRGB(0, 0, width, height, argb, 0, width);
			File tmp = new File(DIR, "frame.png.tmp");
			ImageIO.write(img, "png", tmp);
			move(tmp, new File(DIR, "frame.png"));
		} catch (Exception e) {
			// Never let feedback crash the render loop.
		}
	}

	private static void writeAtomic(String name, byte[] bytes) {
		try {
			File tmp = new File(DIR, name + ".tmp");
			Files.write(tmp.toPath(), bytes);
			move(tmp, new File(DIR, name));
		} catch (Exception e) {
			// ignore
		}
	}

	/** Atomic rename with one retry (Windows can fail the move if the file is being read). */
	private static void move(File tmp, File dest) throws IOException {
		try {
			Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException first) {
			try {
				Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException second) {
				tmp.delete();
				throw second;
			}
		}
	}

	/** Fan output to two streams (console + file). */
	private static final class TeeOutputStream extends OutputStream {
		private final OutputStream a, b;

		TeeOutputStream(OutputStream a, OutputStream b) {
			this.a = a;
			this.b = b;
		}

		@Override public void write(int x) throws IOException { a.write(x); b.write(x); }
		@Override public void write(byte[] buf, int off, int len) throws IOException { a.write(buf, off, len); b.write(buf, off, len); }
		@Override public void flush() throws IOException { a.flush(); b.flush(); }
	}
}
