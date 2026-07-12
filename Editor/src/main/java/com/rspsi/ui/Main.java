package com.rspsi.ui;

/**
 * Plain (non-Application) entry point for the classpath/shadow distribution.
 *
 * Launching a class that {@code extends Application} directly via {@code java -cp}
 * trips JavaFX's "runtime components are missing" check unless FX is on the module
 * path. Bootstrapping through a non-Application main sidesteps that check and lets
 * Application.launch() load JavaFX from the classpath (the shadow jar bundles the FX
 * classes and native libs). Used by the Gradle startScripts main class.
 */
public class Main {
    public static void main(String[] args) {
        LauncherWindow.main(args);
    }
}
