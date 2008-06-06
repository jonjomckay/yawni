package edu.brandeis.cs.steele.wn.browser;

import java.awt.*;
import javax.swing.event.*;
import javax.swing.*;
import javax.swing.text.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import edu.brandeis.cs.steele.util.*;

/**
 * Built for easy extension:
 * - implements ListModel -- built to display large <code>Iterable</code>s
 * - results to display / filter are updated via an <code>Iterable</code> which
 *   is traversed in its own thread -- keeps filtering logic decoupled
 * - implements <code>DocumentListener</code> for search field which will interactively issue
 *   queries via method:
 *     abstract public Iterable search(final String query) 
 *   * this method will be called in a separate thread and should ideally
 *     support thread interruption
 *   - gets query from search field from <code>DocumentEvent</code> via the <code>Document</code>
 * - uses <code>JList</code> reference to maintain display correctness and optimize responsiveness
 * 
 * XXX (older prose design notes) XXX
 * Supports interactive display of a large Iterator/Iterable which (TODO start) fires update
 * events every n adds (n can be picked to fill the visible screen asap) (TODO end).
 * Traverses Iterable in a separate (single) thread and updates the model and
 * periodically signals the view on the event thread
 * (<code>SwingUtilities.invokeAndWait</code>).
 * 
 * TODO Reports the current number of matches for use in a status bar.
 *
 * <b>fireXxx() methods must only be called from the event thread
 * (JCiP, pp. 195) (e.g. via SwingUtilities.invokeAndWait() or
 * SwingUtilities.invokeLater().</b>
 * Inspired by <a href="http://www.oreilly.com/catalog/swinghks/">http://www.oreilly.com/catalog/swinghks/</a>
 */
public abstract class ConcurrentSearchListModel extends AbstractListModel implements DocumentListener {
  private static final long serialVersionUID = 1L;

  private List filterItems;
  private final ExecutorService service;
  private int rowUpdateInterval;

  /** <code>jlist</code> should be focusable <i>if</i> it has contents (ie not empty)
   * <b>and</b> its values are <u>not changing</u>
   */
  private void setUpdating(final boolean updating) {
    jlist.setFocusable(! updating);
  }
  private JList jlist;
  private Future lastTask;

  public ConcurrentSearchListModel() {
    //TODO consider CopyOnWriteArrayList
    this.filterItems = new Vector();
    //this.service = Executors.newSingleThreadExecutor(new DaemonThreadFactory());
    this.service = Executors.newFixedThreadPool(2, new DaemonThreadFactory());
    //setRowUpdateInterval(Integer.MAX_VALUE);
    //setRowUpdateInterval(1);
  }

  abstract public Iterable search(final String query);

  public void setRowUpdateInterval(final int rowUpdateInterval) {
    //TODO ideally just compute this based on visible row range on-the-fly
    //XXX simple, but superior method is to make sure range [0, lastVisibleRow]
    //is updated ASAP, rest don't matter (at expense of complexity, could
    //even optimize further to range [firstVisibleRow, lastVisibleRow]
    if (rowUpdateInterval < 0) {
      throw new IllegalArgumentException("negative rowUpdateInterval "+rowUpdateInterval);
    }
    this.rowUpdateInterval = rowUpdateInterval;
  }
  public void setJList(final JList jlist) {
    assert jlist != null;
    this.jlist = jlist;
    setUpdating(false);
  }
  /** {@inheritDoc */
  public Object getElementAt(final int index) {
    if (index < filterItems.size()) {
      return filterItems.get(index);
    } else {
      return null;
    }
  }
  /** {@inheritDoc */
  public int getSize() {
    return filterItems.size();
  }
  private void redisplay(final Iterable toDisplay) {
    //XXX System.err.println("doRedisplay submitted "+new Date());
    final Future submittedTask = 
      service.submit(new Runnable() {
      public void run() {
        doRedisplay(toDisplay);
        //XXX System.err.println("doRedisplay done      "+new Date());
      }
    });
    if (lastTask != null) {
      final boolean lastTaskCancelled = lastTask.cancel(true);
      if (lastTaskCancelled) {
        System.err.println("lastTaskCancelled: "+lastTaskCancelled+String.format(" %,d", System.currentTimeMillis()));
      }
    }
    lastTask = submittedTask;
  }
  private void doRedisplay(final Iterable toDisplay) {
    // redisplay() should be called in a non-event dispatch thread - it will need a little API
    // Easiest to use a single-threaded ExecutorService
    // - "waiting for work" is trivial
    // ? how do we know if there are any workers in progress ? 
    // ? how do we know if there's any work queued ? (maintain queue of work)
    // - allows cancellation
    // - handles worker daemon-ness (ThreadFactory that creates daemon threads - doesn't need to be shutdown)
    // - handles work thread death
    // ? how to make latest request preempt all others ? bounded buffer of size 1
    // ? maybe ScheduledThreadPoolExecutor - executes last submitted first and tosses others ?
    // 
    // lifecyce
    // - wait for work
    // - execute work, periodically call back view via fireXxx() with SwingUtilities.invokeLater()
    // - interrupt and cancel work if new work arrives
    //
    // If a redisplay() is currently running (in loop below), cancel it at the next opportunity
    // and start new refliter().  Easiest way to accomplish this is to fire partial change events
    // e.g. when first n filtered items are modified, fireContentsChanged(0, n)
    // then as each batch of n changes fire contents changes for that range
    // - fancy responsiveness opti - use these 
    //   - getFirstVisibleIndex()
    //   - getLastVisibleIndex()
    //
    // consider ConcurrentArrayList to ensure no ConcurrentModificationException
    // ? how to ensure view is of latest snapshot of filterItems and not an inconsistent, 
    //   artificial Frankenstein view ?
    // 
    // TODO Consider a delay before acting on this in case there are many in short succession ?
    // Easier to cancel and more responsive looking?
    //
    // Common use case:
    // - many keypresses in short succession which refine a search
    //   - consider a small delay before initiating a search
    //   - could refine initially filtered set
    // - series of keypresses (deletes) which expands a search

    // make sure the search field is always responsive

    final int oldSize = filterItems.size();
    final List newItems = new Vector();
    for (final Object obj : toDisplay) {
      if (Thread.interrupted()) {
        System.err.println("interrupted! newItems.size(): "+newItems.size()+String.format(" %,d", System.currentTimeMillis()));
        break;
      }
      newItems.add(obj);
      //TODO periodically fire interval added (using SwingUtilities.invokeLater()!)
      //FIXME check interrupted() to support cancellation !!!
      //  probably best to do this in search iterators
    }
    try {
      SwingUtilities.invokeAndWait(new Runnable() {
        public void run() {
          // mismatch strategy optimizes common prefixes
          final int s = Utils.mismatch(filterItems, 0, filterItems.size(), newItems, 0);
          if (s != 0) {
            System.err.println("s: "+s);
          }
          setUpdating(true);
          // final int ns = newItems.size();
          // final int os = filterItems.size();
          //
          // fireContentsChanged(this, ...) [0, os) // if ns > os
          // fireIntervalAdded(this, ...) [ns - os, ns) // always
          // fireIntervalRemoved(this, ...) [ns - os, os) if os > ns
          //
          // Note: the fireXxx methods take inclusive ranges, so we need to substract 1
          // from the end points to make these correct
          for (int i = s; i < newItems.size(); i++) {
            if (Thread.interrupted()) {
              System.err.println("alt interrupted!");
            }
            if (i < filterItems.size()) {
              filterItems.set(i, newItems.get(i));
            } else {
              filterItems.add(newItems.get(i));
            }
          }
          if (filterItems.size() > newItems.size()) {
            for (int i = filterItems.size() - 1; i >= newItems.size(); i--) {
              if (Thread.interrupted()) {
                System.err.println("falt interrupted!");
              }
              filterItems.remove(i);
            }
          }
          fireContentsChanged(this, 0, Math.max(getSize(), oldSize));
          setUpdating(false);
          //XXX fireContentsChanged(this, 0, 20);
        }
      });
    } catch(final InterruptedException ie) {
      throw new RuntimeException(ie);
    } catch(final java.lang.reflect.InvocationTargetException ite) {
      throw new RuntimeException(ite);
    }
  }

  /** {@inheritDoc */
  public void changedUpdate(final DocumentEvent evt) { redisplay(search(query(evt))); }
  /** {@inheritDoc */
  public void insertUpdate(final DocumentEvent evt) { redisplay(search(query(evt))); }
  /** {@inheritDoc */
  public void removeUpdate(final DocumentEvent evt) { redisplay(search(query(evt))); }
  
  private String query(final DocumentEvent evt) {
    final Document doc = evt.getDocument();
    try {
      return doc.getText(0, doc.getLength());
    } catch(BadLocationException ble) {
      throw new RuntimeException(ble);
    }
  }

  static class DaemonThreadFactory implements ThreadFactory {
    public Thread newThread(Runnable r) {
      final Thread toReturn = new Thread(r);
      toReturn.setDaemon(true);
      return toReturn;
    }
  } // end class DaemonThreadFactory
}