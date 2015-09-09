/*
 * @(#)JPopupMenu.java 1.169 01/12/05
 *
 * Copyright 2002 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package javax.swing;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.beans.*;
import java.util.Locale;
import java.util.Vector;
import java.util.Hashtable;
import javax.accessibility.*;
import javax.swing.plaf.PopupMenuUI;
import javax.swing.plaf.ComponentUI;
import javax.swing.event.*;
import java.applet.Applet;
/**
 * An implementation of a popup menu -- a small window that pops up
 * and displays a series of choices. A <code>JPopupMenu</code> is used for the
 * menu that appears when the user selects an item on the menu bar.
 * It is also used for "pull-right" menu that appears when the
 * selects a menu item that activates it. Finally, a <code>JPopupMenu</code>
 * can also be used anywhere else you want a menu to appear.  For
 * example, when the user right-clicks in a specified area.
 * <p>
 * For information and examples of using popup menus, see
 * <a
 href="http://java.sun.com/docs/books/tutorial/uiswing/components/menu.html">How to Use Menus</a>
 * in <em>The Java Tutorial.</em>
 * For the keyboard keys used by this component in the standard Look and
 * Feel (L&F) renditions, see the
 * <a href="doc-files/Key-Index.html#JPopupMenu"><code>JPopupMenu</code> key assignments</a>.
 * <p>
 * <strong>Warning:</strong>
 * Serialized objects of this class will not be compatible with
 * future Swing releases. The current serialization support is
 * appropriate for short term storage or RMI between applications running
 * the same version of Swing.  As of 1.4, support for long term storage
 * of all JavaBeans<sup><font size="-2">TM</font></sup>
 * has been added to the <code>java.beans</code> package.
 * Please see {@link java.beans.XMLEncoder}.
 *
 * @beaninfo
 *   attribute: isContainer false
 * description: A small window that pops up and displays a series of choices.
 *
 * @version 1.169 12/05/01
 * @author Georges Saab
 * @author David Karlton
 * @author Arnaud Weber
 */
public class JPopupMenu extends JComponent implements Accessible,MenuElement {
    /**
     * @see #getUIClassID
     * @see #readObject
     */
    private static final String uiClassID = "PopupMenuUI";
    /**
     * Key used in AppContext to determine if light way popups are the default.
     */
    private static final Object defaultLWPopupEnabledKey = 
        new StringBuffer("JPopupMenu.defaultLWPopupEnabledKey");
    /** Bug#4425878-Property javax.swing.adjustPopupLocationToFit introduced */
    static boolean popupPostionFixEnabled = false;
    static {
        popupPostionFixEnabled = java.security.AccessController.doPrivileged(
                new sun.security.action.GetPropertyAction(
                "javax.swing.adjustPopupLocationToFit","")).equals("true"); }
    transient  Component invoker;
    transient  Popup popup;
    transient  Frame frame;
    private    int desiredLocationX,desiredLocationY;
    private    String     label                   = null;
    private    boolean   paintBorder              = true;  
    private    Insets    margin                   = null;
    /**
     * Used to indicate if lightweight popups should be used.
     */
    private    boolean   lightWeightPopup         = true;
    /*
     * Model for the selected subcontrol.
     */
    private SingleSelectionModel selectionModel;
    /* Lock object used in place of class object for synchronization. 
     * (4187686)
     */
    private static final Object classLock = new Object();
    /* diagnostic aids -- should be false for production builds. */
    private static final boolean TRACE =   false; // trace creates and disposes
    private static final boolean VERBOSE = false; // show reuse hits/misses
    private static final boolean DEBUG =   false;  // show bad params, misc.
    /**
     *  Sets the default value of the <code>lightWeightPopupEnabled</code>
     *  property.
     *
     *  @param aFlag <code>true</code> if popups can be lightweight,
     *               otherwise <code>false</code>
     *  @see #getDefaultLightWeightPopupEnabled
     *  @see #setLightWeightPopupEnabled
     */
    public static void setDefaultLightWeightPopupEnabled(boolean aFlag) {
        SwingUtilities.appContextPut(defaultLWPopupEnabledKey, 
                                     new Boolean(aFlag)); }
    /** 
     *  Gets the <code>defaultLightWeightPopupEnabled</code> property,
     *  which by default is <code>true</code>.
     *
     *  @return the value of the <code>defaultLightWeightPopupEnabled</code>
     *          property
     *
     *  @see #setDefaultLightWeightPopupEnabled
     */
    public static boolean getDefaultLightWeightPopupEnabled() {
        Boolean b = (Boolean)
            SwingUtilities.appContextGet(defaultLWPopupEnabledKey);
        if (b == null) {
            SwingUtilities.appContextPut(defaultLWPopupEnabledKey, 
                                         Boolean.TRUE);
            return true; }
        return b.booleanValue(); }
    /**
     * Constructs a <code>JPopupMenu</code> without an "invoker".
     */
    public JPopupMenu() {
        this(null); }
    /**
     * Constructs a <code>JPopupMenu</code> with the specified title.
     *
     * @param label  the string that a UI may use to display as a title 
     * for the popup menu.
     */
    public JPopupMenu(String label) {
        this.label = label;
        lightWeightPopup = getDefaultLightWeightPopupEnabled();
        setSelectionModel(new DefaultSingleSelectionModel());
        addMouseListener(new MouseAdapter() {});
       setFocusTraversalKeysEnabled(false);
        updateUI(); }
    /**
     * Returns the look and feel (L&F) object that renders this component.
     *
     * @return the <code>PopupMenuUI</code> object that renders this component
     */
    public PopupMenuUI getUI() {
        return (PopupMenuUI)ui; }
    /**
     * Sets the L&F object that renders this component.
     *
     * @param ui the new <code>PopupMenuUI</code> L&F object
     * @see UIDefaults#getUI
     * @beaninfo
     *        bound: true
     *       hidden: true
     *    attribute: visualUpdate true
     *  description: The UI object that implements the Component's LookAndFeel. 
     */
    public void setUI(PopupMenuUI ui) {
        super.setUI(ui); }
    /**
     * Resets the UI property to a value from the current look and feel.
     *
     * @see JComponent#updateUI
     */
    public void updateUI() {
        setUI((PopupMenuUI)UIManager.getUI(this)); }
    /**
     * Returns the name of the L&F class that renders this component.
     *
     * @return the string "PopupMenuUI"
     * @see JComponent#getUIClassID
     * @see UIDefaults#getUI
     */
    public String getUIClassID() {
        return uiClassID; }
    protected void processFocusEvent(FocusEvent evt) {
       super.processFocusEvent(evt); }
    /**
     * Processes key stroke events such as mnemonics and accelerators.
     *
     * @param evt  the key event to be processed
     */
    protected void processKeyEvent(KeyEvent evt) {
        MenuSelectionManager.defaultManager().processKeyEvent(evt);
       if (evt.isConsumed()) {
           return; }
       super.processKeyEvent(evt); }
    /**
     * Returns the model object that handles single selections.
     *
     * @return the <code>selectionModel</code> property
     * @see SingleSelectionModel
     */
    public SingleSelectionModel getSelectionModel() {
        return selectionModel; }
    /**
     * Sets the model object to handle single selections.
     *
     * @param model the new <code>SingleSelectionModel</code>
     * @see SingleSelectionModel
     * @beaninfo
     * description: The selection model for the popup menu
     *      expert: true
     */
    public void setSelectionModel(SingleSelectionModel model) {
        selectionModel = model; }
    /**
     * Appends the specified menu item to the end of this menu. 
     *
     * @param c the <code>JMenuItem</code> to add
     * @return the <code>JMenuItem</code> added
     */
    public JMenuItem add(JMenuItem menuItem) {
        super.add(menuItem);
        return menuItem; }
    /**
     * Creates a new menu item with the specified text and appends
     * it to the end of this menu.
     *  
     * @param s the string for the menu item to be added
     */
    public JMenuItem add(String s) {
        return add(new JMenuItem(s)); }
    /**
     * Appends a new menu item to the end of the menu which 
     * dispatches the specified <code>Action</code> object.
     *
     * As of JDK 1.3, this is no longer the preferred method for adding
     * <code>Actions</code> to
     * a container. Instead it is recommended to configure a control with 
     * an action using <code>setAction</code>, and then add that control
     * directly to the <code>Container</code>.
     *
     * @param a the <code>Action</code> to add to the menu
     * @return the new menu item
     * @see Action
     */
    public JMenuItem add(Action a) {
       JMenuItem mi = createActionComponent(a);
       mi.setAction(a);
        add(mi);
        return mi; }
     // Fix for Bug#4425878
    boolean  adjustPopuLocationToFitScreen(Point p) {
        if(popupPostionFixEnabled == false)
            return false;
        int scrWidth = Toolkit.getDefaultToolkit().getScreenSize().width;
        int scrHeight = Toolkit.getDefaultToolkit().getScreenSize().height;
        int oldX = p.x;
        int oldY = p.y;
        Dimension size;
        size = JPopupMenu.this.getPreferredSize();
        if( (p.x + size.width) > scrWidth )
             p.x = scrWidth - size.width;
        if( (p.y + size.height) > scrHeight)
             p.y = scrHeight - size.height;
        /* Change is made to the desired (X,Y) values, when the
           PopupMenu is too tall OR too wide for the screen
        */
        if( p.x < 0 )
            p.x = 0 ;  //oldX;
        if( p.y < 0 )
            p.y = 0; //oldY;
        return true; }
    /**
     * Factory method which creates the <code>JMenuItem</code> for
     * <code>Actions</code> added to the <code>JPopupMenu</code>.
     * As of JDK 1.3, this is no
     * longer the preferred method, instead it is recommended to configure
     * a control with an action using <code>setAction</code>,
     * and then adding that
     * control directly to the <code>Container</code>.
     *
     * @param a the <code>Action</code> for the menu item to be added
     * @return the new menu item
     * @see Action
     *
     * @since 1.3
     */
    protected JMenuItem createActionComponent(Action a) {
        JMenuItem mi = new JMenuItem((String)a.getValue(Action.NAME),
                                     (Icon)a.getValue(Action.SMALL_ICON)){
           protected PropertyChangeListener createActionPropertyChangeListener(Action a) {
             PropertyChangeListener pcl = createActionChangeListener(this);
             if (pcl == null) {
                 pcl = super.createActionPropertyChangeListener(a); }
             return pcl; }
       };
        mi.setHorizontalTextPosition(JButton.TRAILING);
        mi.setVerticalTextPosition(JButton.CENTER);
        mi.setEnabled(a.isEnabled());
       return mi; }
    /**
     * Returns a properly configured <code>PropertyChangeListener</code>
     * which updates the control as changes to the <code>Action</code> occur.  
     * As of JDK 1.3, this is no longer the preferred method for adding
     * <code>Actions</code> to
     * a container. Instead it is recommended to configure a control with 
     * an action using <code>setAction</code>, and then add that control
     * directly to the <code>Container</code>.
     */
    protected PropertyChangeListener createActionChangeListener(JMenuItem b) {
        return new ActionChangedListener(b); }
    private class ActionChangedListener implements PropertyChangeListener, Serializable {
        private JMenuItem menuItem;
        public ActionChangedListener(JMenuItem mi) {
            super();
            setTarget(mi); }
        public void propertyChange(PropertyChangeEvent e) {
            String propertyName = e.getPropertyName();
            if (e.getPropertyName().equals(Action.NAME)) {
                String text = (String) e.getNewValue();
                menuItem.setText(text);
            } else if (propertyName.equals("enabled")) {
                Boolean enabledState = (Boolean) e.getNewValue();
                menuItem.setEnabled(enabledState.booleanValue());
            } else if (e.getPropertyName().equals(Action.SMALL_ICON)) {
                Icon icon = (Icon) e.getNewValue();
                menuItem.setIcon(icon);
                menuItem.invalidate();
                menuItem.repaint(); } }
       public void setTarget(JMenuItem b) {
           this.menuItem = b; } }
    /**
     * Removes the component at the specified index from this popup menu.
     *
     * @param       pos the position of the item to be removed
     * @exception   IllegalArgumentException if the value of 
     *                         <code>pos</code> < 0, or if the value of
     *                      <code>pos</code> is greater than the
     *                      number of items
     */
    public void remove(int pos) {
        if (pos < 0) {
            throw new IllegalArgumentException("index less than zero."); }
        if (pos > getComponentCount() -1) {
            throw new IllegalArgumentException("index greater than the number of items."); }
       super.remove(pos); }
    /**
     * Sets the value of the <code>lightWeightPopupEnabled</code> property,
     * which by default is <code>true</code>.
     * By default, when a look and feel displays a popup, 
     * it can choose to
     * use a lightweight (all-Java) popup.
     * Lightweight popup windows are more efficient than heavyweight 
     * (native peer) windows,
     * but lightweight and heavyweight components do not mix well in a GUI.
     * If your application mixes lightweight and heavyweight components,
     * you should disable lightweight popups.
     * Some look and feels might always use heavyweight popups,
     * no matter what the value of this property.
     *
     * @param aFlag  <code>false</code> to disable lightweight popups
     * @beaninfo
     * description: Determines whether lightweight popups are used when possible
     *      expert: true
     *
     * @see isLightWeightPopupEnabled
     */
    public void setLightWeightPopupEnabled(boolean aFlag) {
        // NOTE: this use to set the flag on a shared JPopupMenu, which meant
        // this effected ALL JPopupMenus.
        lightWeightPopup = aFlag; }
    /**
     * Gets the <code>lightWeightPopupEnabled</code> property.
     *
     * @return the value of the <code>lightWeightPopupEnabled</code> property
     * @see #setLightWeightPopupEnabled
     */
    public boolean isLightWeightPopupEnabled() {
        return lightWeightPopup; }
    /**
     * Returns the popup menu's label
     *
     * @return a string containing the popup menu's label
     * @see #setLabel
     */
    public String getLabel() {
        return label; }
    /**
     * Sets the popup menu's label.  Different look and feels may choose
     * to display or not display this.
     *
     * @param label a string specifying the label for the popup menu
     *
     * @see #setLabel
     * @beaninfo
     * description: The label for the popup menu. 
     *       bound: true
     */
    public void setLabel(String label) {
        String oldValue = this.label;
        this.label = label;
        firePropertyChange("label", oldValue, label);
        if (accessibleContext != null) {
            accessibleContext.firePropertyChange(
                AccessibleContext.ACCESSIBLE_VISIBLE_DATA_PROPERTY,
                oldValue, label); }
        invalidate();
        repaint(); }
    /**
     * Appends a new separator at the end of the menu.
     */
    public void addSeparator() {
        add( new JPopupMenu.Separator() ); }
    /**
     * Inserts a menu item for the specified <code>Action</code> object at 
     * a given position.
     *
     * @param a  the <code>Action</code> object to insert
     * @param index      specifies the position at which to insert the
     *                   <code>Action</code>, where 0 is the first
     * @exception IllegalArgumentException if <code>index</code> < 0
     * @see Action
     */
    public void insert(Action a, int index) {
       JMenuItem mi = createActionComponent(a);
       mi.setAction(a);
        insert(mi, index); }
    /**
     * Inserts the specified component into the menu at a given
     * position.
     *
     * @param component  the <code>Component</code> to insert
     * @param index      specifies the position at which
     *                   to insert the component, where 0 is the first
     * @exception IllegalArgumentException if <code>index</code> < 0
     */
    public void insert(Component component, int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index less than zero."); }
        int nitems = getComponentCount();
       // PENDING(ges): Why not use an array?
        Vector tempItems = new Vector();
        /* Remove the item at index, nitems-index times 
           storing them in a temporary vector in the
           order they appear on the menu.
           */
        for (int i = index ; i < nitems; i++) {
            tempItems.addElement(getComponent(index));
            remove(index); }
        add(component);
        /* Add the removed items back to the menu, they are
           already in the correct order in the temp vector.
           */
        for (int i = 0; i < tempItems.size()  ; i++) {
            add((Component)tempItems.elementAt(i)); } }
    /**
     *  Adds a <code>PopupMenu</code> listener.
     *
     *  @param l  the <code>PopupMenuListener</code> to add
     */
    public void addPopupMenuListener(PopupMenuListener l) {
        listenerList.add(PopupMenuListener.class,l); }
    /**
     * Removes a <code>PopupMenu</code> listener.
     *
     * @param l  the <code>PopupMenuListener</code> to remove
     */
    public void removePopupMenuListener(PopupMenuListener l) {
        listenerList.remove(PopupMenuListener.class,l); }
    /**
     * Returns an array of all the <code>PopupMenuListener</code>s added
     * to this JMenuItem with addPopupMenuListener().
     * 
     * @return all of the <code>PopupMenuListener</code>s added or an empty
     *         array if no listeners have been added
     * @since 1.4
     */
    public PopupMenuListener[] getPopupMenuListeners() {
        return (PopupMenuListener[])listenerList.getListeners(
                PopupMenuListener.class); }
    /**
     * Notifies <code>PopupMenuListener</code>s that this popup menu will 
     * become visible.
     */
    protected void firePopupMenuWillBecomeVisible() {
        Object[] listeners = listenerList.getListenerList();
        PopupMenuEvent e=null;
        for (int i = listeners.length-2; i>=0; i-=2) {
            if (listeners[i]==PopupMenuListener.class) {
                if (e == null)
                    e = new PopupMenuEvent(this);
                ((PopupMenuListener)listeners[i+1]).popupMenuWillBecomeVisible(e); } } }
    /**
     * Notifies <code>PopupMenuListener</code>s that this popup menu will 
     * become invisible.
     */
    protected void firePopupMenuWillBecomeInvisible() {
        Object[] listeners = listenerList.getListenerList();
        PopupMenuEvent e=null;
        for (int i = listeners.length-2; i>=0; i-=2) {
            if (listeners[i]==PopupMenuListener.class) {
                if (e == null)
                    e = new PopupMenuEvent(this);
                ((PopupMenuListener)listeners[i+1]).popupMenuWillBecomeInvisible(e); } } }
    /**
     * Notifies <code>PopupMenuListeners</code> that this popup menu is 
     * cancelled.
     */
    protected void firePopupMenuCanceled() {
        Object[] listeners = listenerList.getListenerList();
        PopupMenuEvent e=null;
        for (int i = listeners.length-2; i>=0; i-=2) {
            if (listeners[i]==PopupMenuListener.class) {
                if (e == null)
                    e = new PopupMenuEvent(this);
                ((PopupMenuListener)listeners[i+1]).popupMenuCanceled(e); } } }
    /**
     * Always returns true since popups, by definition, should always
     * be on top of all other windows.
     * @return true
     */
    // package private
    boolean alwaysOnTop() {
       return true; }
    /**
     * Lays out the container so that it uses the minimum space
     * needed to display its contents.
     */
    public void pack() {
        if(popup != null) {
            Dimension pref = getPreferredSize();
            if (pref == null || pref.width != getWidth() ||
                                pref.height != getHeight()) {
                popup = getPopup(); } } }
    /**
     * Sets the visibility of the popup menu.
     * 
     * @param b true to make the popup visible, or false to
     *          hide it
     * @beaninfo
     *           bound: true
     *     description: Makes the popup visible
     */
    public void setVisible(boolean b) {
       if (DEBUG) {
           System.out.println("JPopupMenu.setVisible " + b); }
        // Is it a no-op?
        if (b == isVisible())
            return;
        // if closing, first close all Submenus
        if (b == false) {
            getSelectionModel().clearSelection();
        } else {
            // This is a popup menu with MenuElement children,
            // set selection path before popping up!
            if (isPopupMenu()) {
             if (getSubElements().length > 0) {
                 MenuElement me[] = new MenuElement[2];
                 me[0]=(MenuElement)this;
                 me[1]=getSubElements()[0];
                 MenuSelectionManager.defaultManager().setSelectedPath(me);
             } else {
                 MenuElement me[] = new MenuElement[1];
                 me[0]=(MenuElement)this;
                 MenuSelectionManager.defaultManager().setSelectedPath(me); } } }
        if(b) {
            firePopupMenuWillBecomeVisible();
            popup = getPopup();
           firePropertyChange("visible", Boolean.FALSE, Boolean.TRUE);
       } else if(popup != null) {
            firePopupMenuWillBecomeInvisible();
            popup.hide();
            popup = null;
           firePropertyChange("visible", Boolean.TRUE, Boolean.FALSE); }
        if (accessibleContext != null) {
           if (b) {
             accessibleContext.firePropertyChange(
                  AccessibleContext.ACCESSIBLE_STATE_PROPERTY,
                  null, AccessibleState.VISIBLE);
           } else {
             accessibleContext.firePropertyChange(
                  AccessibleContext.ACCESSIBLE_STATE_PROPERTY,
                  AccessibleState.VISIBLE, null); } } }
    /**
     * Returns a <code>Popup</code> instance from the
     * <code>PopupMenuUI</code> that has had <code>show</code> invoked on
     * it. If the current <code>popup</code> is non-null,
     * this will invoke <code>dispose</code> of it, and then
     * <code>show</code> the new one.
     * <p>
     * This does NOT fire any events, it is up the caller to dispatch
     * the necessary events.
     */
    private Popup getPopup() {
        Popup oldPopup = popup;
        if (oldPopup != null) {
            oldPopup.hide(); }
        PopupFactory popupFactory = PopupFactory.getSharedInstance();
        if (isLightWeightPopupEnabled()) {
            popupFactory.setPopupType(PopupFactory.LIGHT_WEIGHT_POPUP); }
        else {
            popupFactory.setPopupType(PopupFactory.MEDIUM_WEIGHT_POPUP); }
        // added new
        Point p = new Point(desiredLocationX,desiredLocationY);
        if( adjustPopuLocationToFitScreen(p) == true ) {
             this.desiredLocationX = p.x;
             this.desiredLocationY = p.y; }
        Popup newPopup = getUI().getPopup(this, desiredLocationX,
                                          desiredLocationY);
        popupFactory.setPopupType(PopupFactory.LIGHT_WEIGHT_POPUP);
        newPopup.show();
        return newPopup; }
    /**
     * Returns true if the popup menu is visible (currently
     * being displayed).
     */
    public boolean isVisible() {
       if(popup != null)
           return true;
       else
           return false; }
    /**
     * Sets the location of the upper left corner of the
     * popup menu using x, y coordinates.
     *
     * @param x the x coordinate of the popup's new position
     * @param y the y coordinate of the popup's new position
     * @beaninfo
     * description: The location of the popup menu.
     */
    public void setLocation(int x, int y) {
        int oldX = desiredLocationX;
        int oldY = desiredLocationY;
        desiredLocationX = x;
        desiredLocationY = y;
        if(popup != null && (x != oldX || y != oldY)) {
            popup = getPopup(); } }
    /**
     * Returns true if the popup menu is a standalone popup menu
     * rather than the submenu of a <code>JMenu</code>.
     *
     * @return true if this menu is a standalone popup menu, otherwise false
     */
    private boolean isPopupMenu() {
        return  ((invoker != null) && !(invoker instanceof JMenu)); }
    /**
     * Returns the component which is the 'invoker' of this 
     * popup menu.
     *
     * @return the <code>Component</code> in which the popup menu is displayed
     */
    public Component getInvoker() {
        return this.invoker; }
    /**
     * Sets the invoker of this popup menu -- the component in which
     * the popup menu menu is to be displayed.
     *
     * @param invoker the <code>Component</code> in which the popup
     *         menu is displayed
     * @beaninfo
     * description: The invoking component for the popup menu
     *      expert: true
     */
    public void setInvoker(Component invoker) {
        Component oldInvoker = this.invoker;
        this.invoker = invoker;
        if ((oldInvoker != this.invoker) && (ui != null)) {
            ui.uninstallUI(this);
            ui.installUI(this); }
        invalidate(); }
    /**
     * Displays the popup menu at the position x,y in the coordinate
     * space of the component invoker.
     *
     * @param invoker the component in whose space the popup menu is to appear
     * @param x the x coordinate in invoker's coordinate space at which 
     * the popup menu is to be displayed
     * @param y the y coordinate in invoker's coordinate space at which 
     * the popup menu is to be displayed
     */
    public void show(Component invoker, int x, int y) {
       if (DEBUG) {
           System.out.println("in JPopupMenu.show " ); }
        setInvoker(invoker);
        Frame newFrame = getFrame(invoker);
        if (newFrame != frame) {
            // Use the invoker's frame so that events 
            // are propagated properly
            if (newFrame!=null) {
                this.frame = newFrame;
                if(popup != null) {
                    setVisible(false); } } }
       Point invokerOrigin;
       if (invoker != null) {
           invokerOrigin = invoker.getLocationOnScreen();
           setLocation(invokerOrigin.x + x, 
                  invokerOrigin.y + y);
       } else {
           setLocation(x, y); }
        setVisible(true);        }
    /**
     * Returns the popup menu which is at the root of the menu system
     * for this popup menu.
     *
     * @return the topmost grandparent <code>JPopupMenu</code>
     */
    JPopupMenu getRootPopupMenu() {
        JPopupMenu mp = this;
        while((mp!=null) && (mp.isPopupMenu()!=true) &&
              (mp.getInvoker() != null) &&
              (mp.getInvoker().getParent() != null) &&
              (mp.getInvoker().getParent() instanceof JPopupMenu)
              ) {
            mp = (JPopupMenu) mp.getInvoker().getParent(); }
        return mp; }
    /**
     * Returns the component at the specified index.
     * 
     * @param i  the index of the component, where 0 is the first 
     * @return the <code>Component</code> at that index 
     * @deprecated replaced by <code>getComponent(int i)</code>
     */
    public Component getComponentAtIndex(int i) {
        return getComponent(i); }
    /**
     * Returns the index of the specified component.
     * 
     * @param  the <code>Component</code> to find
     * @return the index of the component, where 0 is the first;
     *         or -1 if the component is not found
     */
    public int getComponentIndex(Component c) {
        int ncomponents = this.getComponentCount();
        Component[] component = this.getComponents();
        for (int i = 0 ; i < ncomponents ; i++) {
            Component comp = component[i];
            if (comp == c) 
                return i; }
        return -1; }
    /**
     * Sets the size of the Popup window using a <code>Dimension</code> object.
     * This is equivalent to <code>setPreferredSize(d)</code>.
     *
     * @param d   the <code>Dimension</code> specifying the new size 
     * of this component.
     * @beaninfo
     * description: The size of the popup menu
     */
    public void setPopupSize(Dimension d) {
        Dimension oldSize = getPreferredSize();
        setPreferredSize(d);
        if (popup != null) {
            Dimension newSize = getPreferredSize();
            if (!oldSize.equals(newSize)) {
                popup = getPopup(); } } }
    /**
     * Sets the size of the Popup window to the specified width and
     * height. This is equivalent to
     *  <code>setPreferredSize(new Dimension(width, height))</code>.
     *
     * @param <code>width</code> the new width of the Popup in pixels
     * @param <code>height</code> the new height of the Popup in pixels
     * @beaninfo
     * description: The size of the popup menu
     */
    public void setPopupSize(int width, int height) {
        setPopupSize(new Dimension(width, height)); }
    /**
     * Sets the currently selected component,  This will result
     * in a change to the selection model.
     *
     * @param sel the <code>Component</code> to select
     * @beaninfo
     * description: The selected component on the popup menu
     *      expert: true
     *      hidden: true
     */
    public void setSelected(Component sel) {    
        SingleSelectionModel model = getSelectionModel();
        int index = getComponentIndex(sel);
        model.setSelectedIndex(index); }
    /**
     * Checks whether the border should be painted.
     *
     * @return true if the border is painted, false otherwise
     * @see #setBorderPainted
     */
    public boolean isBorderPainted() {
        return paintBorder; }
    /**
     * Sets whether the border should be painted.
     *
     * @param b if true, the border is painted.
     * @see #isBorderPainted
     * @beaninfo
     * description: Is the border of the popup menu painted
     */
    public void setBorderPainted(boolean b) {
        paintBorder = b;
        repaint(); }
    /**
     * Paints the popup menu's border if the <code>borderPainted</code>
     * property is <code>true</code>.
     * @param g  the <code>Graphics</code> object
     * 
     * @see JComponent#paint
     * @see JComponent#setBorder
     */
    protected void paintBorder(Graphics g) {    
        if (isBorderPainted()) {
            super.paintBorder(g); } }
    /**
     * Returns the margin, in pixels, between the popup menu's border and
     * its containees.
     *
     * @return an <code>Insets</code> object containing the margin values.
     */
    public Insets getMargin() {
        if(margin == null) {
            return new Insets(0,0,0,0);
        } else {
            return margin; } }
    /**
     * Examines the list of menu items to determine whether
     * <code>popup</code> is a popup menu.
     * 
     * @param popup  a <code>JPopupMenu</code>
     * @return true if <code>popup</code>
     */
    boolean isSubPopupMenu(JPopupMenu popup) {
        int ncomponents = this.getComponentCount();
        Component[] component = this.getComponents();
        for (int i = 0 ; i < ncomponents ; i++) {
            Component comp = component[i];
            if (comp instanceof JMenu) {
                JMenu menu = (JMenu)comp;
                JPopupMenu subPopup = menu.getPopupMenu();
                if (subPopup == popup)
                    return true;
                if (subPopup.isSubPopupMenu(popup))
                    return true; } }
        return false; }
    private static Frame getFrame(Component c) {
        Component w = c;
        while(!(w instanceof Frame) && (w!=null)) {
            w = w.getParent(); }
        return (Frame)w; }
    /**
     * Returns a string representation of this <code>JPopupMenu</code>.
     * This method 
     * is intended to be used only for debugging purposes, and the 
     * content and format of the returned string may vary between      
     * implementations. The returned string may be empty but may not 
     * be <code>null</code>.
     * 
     * @return  a string representation of this <code>JPopupMenu</code>.
     */
    protected String paramString() {
       String labelString = (label != null ?
                        label : "");
       String paintBorderString = (paintBorder ?
                          "true" : "false");
       String marginString = (margin != null ?
                        margin.toString() : "");
       String lightWeightPopupEnabledString = (isLightWeightPopupEnabled() ?
                           "true" : "false");       
       return super.paramString() +
           ",desiredLocationX=" + desiredLocationX +
           ",desiredLocationY=" + desiredLocationY +
       ",label=" + labelString +
       ",lightWeightPopupEnabled=" + lightWeightPopupEnabledString +
       ",margin=" + marginString +
       ",paintBorder=" + paintBorderString; }
    /**
     * Gets the AccessibleContext associated with this JPopupMenu. 
     * For JPopupMenus, the AccessibleContext takes the form of an 
     * AccessibleJPopupMenu. 
     * A new AccessibleJPopupMenu instance is created if necessary.
     *
     * @return an AccessibleJPopupMenu that serves as the 
     *         AccessibleContext of this JPopupMenu
     */
    public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) {
            accessibleContext = new AccessibleJPopupMenu(); }
        return accessibleContext; }
    /**
     * This class implements accessibility support for the 
     * <code>JPopupMenu</code> class.  It provides an implementation of the 
     * Java Accessibility API appropriate to popup menu user-interface 
     * elements.
     */
    protected class AccessibleJPopupMenu extends AccessibleJComponent {
        /**
         * Get the role of this object.
         *
         * @return an instance of AccessibleRole describing the role of 
         * the object
         */
        public AccessibleRole getAccessibleRole() {
            return AccessibleRole.POPUP_MENU; }
    } // inner class AccessibleJPopupMenu
    private void writeObject(ObjectOutputStream s) throws IOException {
        Vector      values = new Vector();
        s.defaultWriteObject();
        // Save the invoker, if its Serializable.
        if(invoker != null && invoker instanceof Serializable) {
            values.addElement("invoker");
            values.addElement(invoker); }
        // Save the popup, if its Serializable.
        if(popup != null && popup instanceof Serializable) {
            values.addElement("popup");
            values.addElement(popup); }
        // Save the frame, if its Serializable.
        if(frame != null && frame instanceof Serializable) {
            values.addElement("frame");
            values.addElement(frame); }
        s.writeObject(values);
        if (getUIClassID().equals(uiClassID)) {
            byte count = JComponent.getWriteObjCounter(this);
            JComponent.setWriteObjCounter(this, --count);
            if (count == 0 && ui != null) {
                ui.installUI(this); } } }
    // implements javax.swing.MenuElement
    private void readObject(ObjectInputStream s) 
        throws IOException, ClassNotFoundException {
        s.defaultReadObject();
        Vector          values = (Vector)s.readObject();
        int             indexCounter = 0;
        int             maxCounter = values.size();
        if(indexCounter < maxCounter && values.elementAt(indexCounter).
           equals("invoker")) {
            invoker = (Component)values.elementAt(++indexCounter);
            indexCounter++; }
        if(indexCounter < maxCounter && values.elementAt(indexCounter).
           equals("popup")) {
            popup = (Popup)values.elementAt(++indexCounter);
            indexCounter++; }
        if(indexCounter < maxCounter && values.elementAt(indexCounter).
           equals("frame")) {
            frame = (Frame)values.elementAt(++indexCounter);
            indexCounter++; } }
    /**
     * This method is required to conform to the
     * <code>MenuElement</code> interface, but it not implemented.
     * @see MenuElement#processMouseEvent(MouseEvent, MenuElement[], MenuSelectionManager)
     */
    public void processMouseEvent(MouseEvent event,MenuElement path[],MenuSelectionManager manager) {}
    /**
     * This method is required to conform to the
     * <code>MenuElement</code> interface, but it not implemented.
     * @see MenuElement#processKeyEvent(KeyEvent, MenuElement[], MenuSelectionManager)
     */
    public void processKeyEvent(KeyEvent e,MenuElement path[],MenuSelectionManager manager) { }
    /**
     * Messaged when the menubar selection changes to activate or
     * deactivate this menu. This implements the
     * <code>javax.swing.MenuElement</code> interface.
     * Overrides <code>MenuElement.menuSelectionChanged</code>.
     *
     * @param isIncluded  true if this menu is active, false if
     *        it is not
     * @see MenuElement#menuSelectionChanged(boolean)
     */
    public void menuSelectionChanged(boolean isIncluded) {
       if (DEBUG) {
           System.out.println("In JPopupMenu.menuSelectionChanged " + isIncluded); }
        if(invoker instanceof JMenu) {
            JMenu m = (JMenu) invoker;
            if(isIncluded) 
                m.setPopupMenuVisible(true);
            else
                m.setPopupMenuVisible(false); }
        if (isPopupMenu() && !isIncluded)
          setVisible(false); }
    /**
     * Returns an array of <code>MenuElement</code>s containing the submenu
     * for this menu component.  It will only return items conforming to
     * the <code>JMenuElement</code> interface.
     * If popup menu is <code>null</code> returns
     * an empty array.  This method is required to conform to the
     * <code>MenuElement</code> interface.  
     *
     * @return an array of <code>MenuElement</code> objects
     * @see MenuElement#getSubElements
     */
    public MenuElement[] getSubElements() {
        MenuElement result[];
        Vector tmp = new Vector();
        int c = getComponentCount();
        int i;
        Component m;
        for(i=0 ; i < c ; i++) {
            m = getComponent(i);
            if(m instanceof MenuElement)
                tmp.addElement(m); }
        result = new MenuElement[tmp.size()];
        for(i=0,c=tmp.size() ; i < c ; i++) 
            result[i] = (MenuElement) tmp.elementAt(i);
        return result; }
    /**
     * Returns this <code>JPopupMenu</code> component.
     * @return this <code>JPopupMenu</code> object
     * @see MenuElement#getComponent
     */
    public Component getComponent() {
        return this; }
    /**
     * A popup menu-specific separator.
     */
    static public class Separator extends JSeparator {
        public Separator( ) {
           super( JSeparator.HORIZONTAL ); }
        /**
        * Returns the name of the L&F class that renders this component.
        *
        * @return the string "PopupMenuSeparatorUI"
        * @see JComponent#getUIClassID
        * @see UIDefaults#getUI
        */
        public String getUIClassID() {
            return "PopupMenuSeparatorUI"; } }
    /**
     * Returns true if the <code>MouseEvent</code> is considered a popup trigger
     * by the <code>JPopupMenu</code>'s currently installed UI.
     *
     * @return true if the mouse event is a popup trigger
     * @since 1.3
     */
    public boolean isPopupTrigger(MouseEvent e) {
       return getUI().isPopupTrigger(e); } }
