package edu.brandeis.cs.steele.wn.browser;

import java.io.*;
import javax.swing.*;
import java.awt.event.*;
import java.awt.*;
import java.util.*;
import java.util.prefs.*;

/**
 * Save window positions and all other persistent user preferences.
 * Doesn't use Properties (files), uses Preferences - no files or maps to manage.
 * TODO rename this class
 * @author http://www.oreilly.com/catalog/swinghks/
 */
class WindowSaver implements AWTEventListener {
  // import/export preferences
  // good article
  // http://blogs.sun.com/CoreJavaTechTips/entry/the_preferences_api

  private static Preferences prefs = Preferences.userNodeForPackage(WindowSaver.class);
  private static WindowSaver saver;

  static {
    // on class load, register self as saver
    //XXX Toolkit.getDefaultToolkit().addAWTEventListener(
    //XXX    WindowSaver.getInstance(), AWTEvent.WINDOW_EVENT_MASK);

    //try {
    //  prefs.clear();
    //} catch(BackingStoreException bse) {
    //  bse.printStackTrace();
    //}
  };

  static void loadDefaults() {
    // TODO 
    // see if the defaults have been loaded,
    //   if not, load them
    // * Don't clobber user preferences
    // Parallel/isomorphic tree for user preferences and defaults
    // - like the package structure of src/java/ and test/
    // - user
    // - default
    // Add a revision number or something to determine the defaults tree version
    // Preferences.userNodeForPackage(Class<?> c)
    //
    // TODO how to represent search history ?
    // Note: when encoding values, there is a maximum size: 
    // Preferences.MAX_VALUE_LENGTH characters

    final InputStream is = WindowSaver.class.getResourceAsStream("defaults.xml");
    try {
      Preferences.importPreferences(is); 
      is.close();
    } catch(InvalidPreferencesFormatException ipfe) {
      throw new RuntimeException(ipfe);
    } catch(IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  static void setLookAndFeel() {
    //TODO loadDefaults();
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      //UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
    } catch(Exception e) {
      System.err.println("Error setting native LAF: " + e);
    }
  }

  public static WindowSaver getInstance() {
    if (saver == null) {
      saver = new WindowSaver();
    }
    return saver;
  }

  private WindowSaver() {
    final InputStream is = WindowSaver.class.getResourceAsStream("defaults.xml");
    //try {
    //  final BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
    //  String line;
    //  while((line = reader.readLine()) != null) {
    //    System.err.println(line);
    //  }
    //  reader.close();
    //} catch(IOException ioe) {
    //  throw new RuntimeException(ioe);
    //}
    //
  }

  /** {@inheritDoc} */
  public void eventDispatched(final AWTEvent evt) {
    //System.err.println("event: " + evt);
    if (evt.getID() == WindowEvent.WINDOW_CLOSING) {
      final ComponentEvent cev = (ComponentEvent)evt;
      if (cev.getComponent() instanceof JFrame) {
        //System.err.println("closing event: " + evt);
        final JFrame frame = (JFrame)cev.getComponent();
        final String name = frame.getName();
        if (name.startsWith("edu.brandeis.cs.steele.wn.browser") == false) {
          return;
        }
        //XXX saveSettings(frame);
        //covered by WindowClosing listener
      }
    }
    if (evt.getID() == WindowEvent.WINDOW_OPENED) {
      final ComponentEvent cev = (ComponentEvent)evt;
      if (cev.getComponent() instanceof JFrame) {
        //System.err.println("closing event: " + evt);
        final JFrame frame = (JFrame)cev.getComponent();
        final String name = frame.getName();
        if (name.startsWith("edu.brandeis.cs.steele.wn.browser") == false) {
          return;
        }
        loadSettings(frame);
      }
    }
  }
  
  // TODO use background thread to update current window position on
  // move or use an ugly shutdown hook to make sure saves work

  static void loadSettings(final JFrame frame) {
    // "Window settings"
    final String name = frame.getName();
    //System.err.println("load name: " + name);
    final int x = prefs.getInt(name + ".x", -1);
    final int y = prefs.getInt(name + ".y", -1);
    final int w = prefs.getInt(name + ".width", 640);
    final int h = prefs.getInt(name + ".height", 480);
    //FIXME interpret width / height 0 as preferred
    frame.setSize(new Dimension(w, h));
    frame.validate();

    if(x >= 0 && y >= 0) {
      frame.setLocation(x, y);
    } else {
      frame.setLocationRelativeTo(null);
    }
  }

  static void saveSettings(final JFrame frame) {
    final String name = frame.getName();
    System.err.println("save name: " + name);
    prefs.putInt(name + ".x", frame.getX());
    prefs.putInt(name + ".y", frame.getY());
    prefs.putInt(name + ".width", frame.getWidth());
    prefs.putInt(name + ".height", frame.getHeight());
  }
}
