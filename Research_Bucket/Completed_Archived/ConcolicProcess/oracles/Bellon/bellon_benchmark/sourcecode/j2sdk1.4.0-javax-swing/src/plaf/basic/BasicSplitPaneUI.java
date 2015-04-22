/*
 * @(#)BasicSplitPaneUI.java   1.68 01/12/03
 *
 * Copyright 2002 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package javax.swing.plaf.basic;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.peer.ComponentPeer;
import java.awt.peer.LightweightPeer;
import java.beans.*;
import java.util.*;
import javax.swing.plaf.ActionMapUIResource;
import javax.swing.plaf.SplitPaneUI;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
/**
 * A Basic L&F implementation of the SplitPaneUI.
 *
 * @version 1.68 12/03/01
 * @author Scott Violet
 * @author Steve Wilson
 * @author Ralph Kar
 */
public class BasicSplitPaneUI extends SplitPaneUI {
    /**
     * The divider used for non-continuous layout is added to the split pane
     * with this object.
     */
    protected static final String NON_CONTINUOUS_DIVIDER =
        "nonContinuousDivider";
    /**
     * How far (relativ) the divider does move when it is moved around by
     * the cursor keys on the keyboard.
     */
    protected static int KEYBOARD_DIVIDER_MOVE_OFFSET = 3;
    /**
     * JSplitPane instance this instance is providing
     * the look and feel for.
     */
    protected JSplitPane splitPane;
    /**
     * LayoutManager that is created and placed into the split pane.
     */
    protected BasicHorizontalLayoutManager layoutManager;
    /**
     * Instance of the divider for this JSplitPane.
     */
    protected BasicSplitPaneDivider divider;
    /**
     * Instance of the PropertyChangeListener for this JSplitPane.
     */
    protected PropertyChangeListener propertyChangeListener;
    /**
     * Instance of the FocusListener for this JSplitPane.
     */
    protected FocusListener focusListener;
    /**
     * The size of the divider while the dragging session is valid.
     */
    protected int dividerSize;
    /**
     * Instance for the shadow of the divider when non continuous layout
     * is being used.
     */
    protected Component nonContinuousLayoutDivider;
    /**
     * Set to true in startDragging if any of the children
     * (not including the nonContinuousLayoutDivider) are heavy weights.
     */
    protected boolean draggingHW;
    /**
     * Location of the divider when the dragging session began.
     */
    protected int beginDragDividerLocation;
    /**
     * As of Java 2 platform v1.3 this previously undocumented field is no
     * longer used.
     * Key bindings are now defined by the LookAndFeel, please refer to
     * the key bindings specification for further details.
     *
     * @deprecated As of Java 2 platform v1.3.
     */
    protected KeyStroke upKey;
    /**
     * As of Java 2 platform v1.3 this previously undocumented field is no
     * longer used.
     * Key bindings are now defined by the LookAndFeel, please refer to
     * the key bindings specification for further details.
     *
     * @deprecated As of Java 2 platform v1.3.
     */
    protected KeyStroke downKey;
    /**
     * As of Java 2 platform v1.3 this previously undocumented field is no
     * longer used.
     * Key bindings are now defined by the LookAndFeel, please refer to
     * the key bindings specification for further details.
     *
     * @deprecated As of Java 2 platform v1.3.
     */
    protected KeyStroke leftKey;
    /**
     * As of Java 2 platform v1.3 this previously undocumented field is no
     * longer used.
     * Key bindings are now defined by the LookAndFeel, please refer to
     * the key bindings specification for further details.
     *
     * @deprecated As of Java 2 platform v1.3.
     */
    protected KeyStroke rightKey;
    /**
     * As of Java 2 platform v1.3 this previously undocumented field is no
     * longer used.
     * Key bindings are now defined by the LookAndFeel, please refer to
     * the key bindings specification for further details.
     *
     * @deprecated As of Java 2 platform v1.3.
     */
    protected KeyStroke homeKey;
    /**
     * As of Java 2 platform v1.3 this previously undocumented field is no
     * longer used.
     * Key bindings are now defined by the LookAndFeel, please refer to
     * the key bindings specification for further details.
     *
     * @deprecated As of Java 2 platform v1.3.
     */
    protected KeyStroke endKey;
    /**
     * As of Java 2 platform v1.3 this previously undocumented field is no
     * longer used.
     * Key bindings are now defined by the LookAndFeel, please refer to
     * the key bindings specification for further details.
     *
     * @deprecated As of Java 2 platform v1.3.
     */
    protected KeyStroke dividerResizeToggleKey;
    /**
     * As of Java 2 platform v1.3 this previously undocumented field is no
     * longer used.
     * Key bindings are now defined by the LookAndFeel, please refer to
     * the key bindings specification for further details.
     *
     * @deprecated As of Java 2 platform v1.3.
     */
    protected ActionListener keyboardUpLeftListener;
    /**
     * As of Java 2 platform v1.3 this previously undocumented field is no
     * longer used.
     * Key bindings are now defined by the LookAndFeel, please refer to
     * the key bindings specification for further details.
     *
     * @deprecated As of Java 2 platform v1.3.
     */
    protected ActionListener keyboardDownRightListener;
    /**
     * As of Java 2 platform v1.3 this previously undocumented field is no
     * longer used.
     * Key bindings are now defined by the LookAndFeel, please refer to
     * the key bindings specification for further details.
     *
     * @deprecated As of Java 2 platform v1.3.
     */
    protected ActionListener keyboardHomeListener;
    /**
     * As of Java 2 platform v1.3 this previously undocumented field is no
     * longer used.
     * Key bindings are now defined by the LookAndFeel, please refer to
     * the key bindings specification for further details.
     *
     * @deprecated As of Java 2 platform v1.3.
     */
    protected ActionListener keyboardEndListener;
    /**
     * As of Java 2 platform v1.3 this previously undocumented field is no
     * longer used.
     * Key bindings are now defined by the LookAndFeel, please refer to
     * the key bindings specification for further details.
     *
     * @deprecated As of Java 2 platform v1.3.
     */
    protected ActionListener keyboardResizeToggleListener;
    // Private data of the instance
    private int         orientation;
    private int         lastDragLocation;
    private boolean     continuousLayout;
    private boolean     dividerKeyboardResize;
    private boolean     dividerLocationIsSet;  // needed for tracking
                                               // the first occurrence of
                                               // setDividerLocation()
    private boolean rememberPaneSizes;
    /** Indicates that we have painted once. */
    // This is used by the LayoutManger to determine when it should use
    // the divider location provided by the JSplitPane. This is used as there
    // is no way to determine when the layout process has completed.
    boolean             painted;
    /** If true, setDividerLocation does nothing. */
    boolean             ignoreDividerLocationChange;
    /**
     * Creates a new BasicSplitPaneUI instance
     */
    public static ComponentUI createUI(JComponent x) {
        return new BasicSplitPaneUI(); }
    /**
     * Installs the UI.
     */
    public void installUI(JComponent c) {
        splitPane = (JSplitPane) c;
        dividerLocationIsSet = false;
        dividerKeyboardResize = false;
        installDefaults();
        installListeners();
        installKeyboardActions();
        setLastDragLocation(-1); }
    /**
     * Installs the UI defaults.
     */
    protected void installDefaults(){ 
        LookAndFeel.installBorder(splitPane, "SplitPane.border");
        LookAndFeel.installColors(splitPane, "SplitPane.background",
                                  "SplitPane.foreground");
        if (divider == null) divider = createDefaultDivider();
        divider.setBasicSplitPaneUI(this);
       Border    b = divider.getBorder();
       if (b == null || !(b instanceof UIResource)) {
           divider.setBorder(UIManager.getBorder("SplitPaneDivider.border")); }
        setOrientation(splitPane.getOrientation());
       // This plus 2 here is to provide backwards consistancy. Previously,
       // the old size did not include the 2 pixel border around the divider,
       // it now does.
        splitPane.setDividerSize(((Integer) (UIManager.get(
            "SplitPane.dividerSize"))).intValue());
        divider.setDividerSize(splitPane.getDividerSize());
       dividerSize = divider.getDividerSize();
        splitPane.add(divider, JSplitPane.DIVIDER);
        setContinuousLayout(splitPane.isContinuousLayout());
        resetLayoutManager();
        /* Install the nonContinuousLayoutDivider here to avoid having to
        add/remove everything later. */
        if(nonContinuousLayoutDivider == null) {
            setNonContinuousLayoutDivider(
                                createDefaultNonContinuousLayoutDivider(),
                                true);
        } else {
            setNonContinuousLayoutDivider(nonContinuousLayoutDivider, true); } }
    /**
     * Installs the event listeners for the UI.
     */
    protected void installListeners() {
        if ((propertyChangeListener = createPropertyChangeListener()) !=
            null) {
            splitPane.addPropertyChangeListener(propertyChangeListener); }
        if ((focusListener = createFocusListener()) != null) {
            splitPane.addFocusListener(focusListener); } }
    /**
     * Installs the keyboard actions for the UI.
     */
    protected void installKeyboardActions() {
       InputMap km = getInputMap(JComponent.
                        WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
       SwingUtilities.replaceUIInputMap(splitPane, JComponent.
                             WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
                             km);
       ActionMap am = getActionMap();
       SwingUtilities.replaceUIActionMap(splitPane, am); }
    InputMap getInputMap(int condition) {
       if (condition == JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) {
           return (InputMap)UIManager.get("SplitPane.ancestorInputMap"); }
       return null; }
    ActionMap getActionMap() {
       ActionMap map = (ActionMap)UIManager.get("SplitPane.actionMap");
       if (map == null) {
           map = createActionMap();
           if (map != null) {
             UIManager.getLookAndFeelDefaults().put("SplitPane.actionMap",
                                                       map); } }
       return map; }
    ActionMap createActionMap() {
       ActionMap map = new ActionMapUIResource();
        map.put("negativeIncrement", new KeyboardUpLeftAction());
       map.put("positiveIncrement", new KeyboardDownRightAction());
       map.put("selectMin", new KeyboardHomeAction());
       map.put("selectMax", new KeyboardEndAction());
       map.put("startResize", new KeyboardResizeToggleAction());
       map.put("toggleFocus", new ToggleSideFocusAction());
       return map; }
    /**
     * Uninstalls the UI.
     */
    public void uninstallUI(JComponent c) {
        uninstallKeyboardActions();
        uninstallListeners();
        uninstallDefaults();
        dividerLocationIsSet = false;
        dividerKeyboardResize = false;
        splitPane = null; }
    /**
     * Uninstalls the UI defaults.
     */
    protected void uninstallDefaults() {
        if(splitPane.getLayout() == layoutManager) {
            splitPane.setLayout(null); }
        if(nonContinuousLayoutDivider != null) {
            splitPane.remove(nonContinuousLayoutDivider); }
        LookAndFeel.uninstallBorder(splitPane);
       Border    b = divider.getBorder();
       if (b instanceof UIResource) {
           divider.setBorder(null); }
        splitPane.remove(divider);
        divider.setBasicSplitPaneUI(null);
        layoutManager = null;
        divider = null;
        nonContinuousLayoutDivider = null;
        setNonContinuousLayoutDivider(null); }
    /**
     * Uninstalls the event listeners for the UI.
     */
    protected void uninstallListeners() {
        if (propertyChangeListener != null) {
            splitPane.removePropertyChangeListener(propertyChangeListener);
            propertyChangeListener = null; }
        if (focusListener != null) {
            splitPane.removeFocusListener(focusListener);
            focusListener = null; }
        keyboardUpLeftListener = null;
        keyboardDownRightListener = null;
        keyboardHomeListener = null;
        keyboardEndListener = null;
        keyboardResizeToggleListener = null; }
    /**
     * Uninstalls the keyboard actions for the UI.
     */
    protected void uninstallKeyboardActions() {
       SwingUtilities.replaceUIActionMap(splitPane, null);
       SwingUtilities.replaceUIInputMap(splitPane, JComponent.
                            WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
                            null); }
    /**
     * Creates a PropertyChangeListener for the JSplitPane UI.
     */
    protected PropertyChangeListener createPropertyChangeListener() {
        return new PropertyHandler(); }
    /**
     * Creates a FocusListener for the JSplitPane UI.
     */
    protected FocusListener createFocusListener() {
        return new FocusHandler(); }
    /**
     * As of Java 2 platform v1.3 this method is no
     * longer used. Subclassers previously using this method should
     * instead create an Action wrapping the ActionListener, and register
     * that Action by overriding <code>installKeyboardActions</code> and
     * placing the Action in the SplitPane's ActionMap. Please refer to
     * the key bindings specification for further details.
     * <p>
     * Creates a ActionListener for the JSplitPane UI that listens for
     * specific key presses.
     *
     * @deprecated As of Java 2 platform v1.3.
     */
    protected ActionListener createKeyboardUpLeftListener() {
        return new KeyboardUpLeftHandler(); }
    /**
     * As of Java 2 platform v1.3 this method is no
     * longer used. Subclassers previously using this method should
     * instead create an Action wrapping the ActionListener, and register
     * that Action by overriding <code>installKeyboardActions</code> and
     * placing the Action in the SplitPane's ActionMap. Please refer to
     * the key bindings specification for further details.
     * <p>
     * Creates a ActionListener for the JSplitPane UI that listens for
     * specific key presses.
     *
     * @deprecated As of Java 2 platform v1.3.
     */
    protected ActionListener createKeyboardDownRightListener() {
        return new KeyboardDownRightHandler(); }
    /**
     * As of Java 2 platform v1.3 this method is no
     * longer used. Subclassers previously using this method should
     * instead create an Action wrapping the ActionListener, and register
     * that Action by overriding <code>installKeyboardActions</code> and
     * placing the Action in the SplitPane's ActionMap. Please refer to
     * the key bindings specification for further details.
     * <p>
     * Creates a ActionListener for the JSplitPane UI that listens for
     * specific key presses.
     *
     * @deprecated As of Java 2 platform v1.3.
     */
    protected ActionListener createKeyboardHomeListener() {
        return new KeyboardHomeHandler(); }
    /**
     * As of Java 2 platform v1.3 this method is no
     * longer used. Subclassers previously using this method should
     * instead create an Action wrapping the ActionListener, and register
     * that Action by overriding <code>installKeyboardActions</code> and
     * placing the Action in the SplitPane's ActionMap. Please refer to
     * the key bindings specification for further details.
     * <p>
     * Creates a ActionListener for the JSplitPane UI that listens for
     * specific key presses.
     *
     * @deprecated As of Java 2 platform v1.3.
     */
    protected ActionListener createKeyboardEndListener() {
        return new KeyboardEndHandler(); }
    /**
     * As of Java 2 platform v1.3 this method is no
     * longer used. Subclassers previously using this method should
     * instead create an Action wrapping the ActionListener, and register
     * that Action by overriding <code>installKeyboardActions</code> and
     * placing the Action in the SplitPane's ActionMap. Please refer to
     * the key bindings specification for further details.
     * <p>
     * Creates a ActionListener for the JSplitPane UI that listens for
     * specific key presses.
     *
     * @deprecated As of Java 2 platform v1.3.
     */
    protected ActionListener createKeyboardResizeToggleListener() {
        return new KeyboardResizeToggleHandler(); }
    /**
     * Returns the orientation for the JSplitPane.
     */
    public int getOrientation() {
        return orientation; }
    /**
     * Set the orientation for the JSplitPane.
     */
    public void setOrientation(int orientation) {
        this.orientation = orientation; }
    /**
     * Determines wether the JSplitPane is set to use a continuous layout.
     */
    public boolean isContinuousLayout() {
        return continuousLayout; }
    /**
     * Turn continuous layout on/off.
     */
    public void setContinuousLayout(boolean b) {
        continuousLayout = b; }
    /**
     * Returns the last drag location of the JSplitPane.
     */
    public int getLastDragLocation() {
        return lastDragLocation; }
    /**
     * Set the last drag location of the JSplitPane.
     */
    public void setLastDragLocation(int l) {
        lastDragLocation = l; }
    /**
     * @return increment via keyboard methods.
     */
    int getKeyboardMoveIncrement() {
       return KEYBOARD_DIVIDER_MOVE_OFFSET; }
    /**
     * Implementation of the PropertyChangeListener
     * that the JSplitPane UI uses.
     * <p>
     * This inner class is marked &quot;public&quot; due to a compiler bug.
     * This class should be treated as a &quot;protected&quot; inner class.
     * Instantiate it only within subclasses of BasicSplitPaneUI.
     */
    public class PropertyHandler implements PropertyChangeListener {
        /**
         * Messaged from the <code>JSplitPane</code> the receiver is
         * contained in.  May potentially reset the layout manager and cause a
         * <code>validate</code> to be sent.
         */
        public void propertyChange(PropertyChangeEvent e) {
            if(e.getSource() == splitPane) {
                String changeName = e.getPropertyName();
                if(changeName.equals(JSplitPane.ORIENTATION_PROPERTY)) {
                    orientation = splitPane.getOrientation();
                    resetLayoutManager();
                } else if(changeName.equals(
                                    JSplitPane.CONTINUOUS_LAYOUT_PROPERTY)) {
                    setContinuousLayout(splitPane.isContinuousLayout());
                    if(!isContinuousLayout()) {
                        if(nonContinuousLayoutDivider == null) {
                            setNonContinuousLayoutDivider(
                                createDefaultNonContinuousLayoutDivider(),
                                true);
                        } else if(nonContinuousLayoutDivider.getParent() ==
                                  null) {
                            setNonContinuousLayoutDivider(
                                nonContinuousLayoutDivider,
                                true); } }
                } else if(changeName.equals(JSplitPane.DIVIDER_SIZE_PROPERTY)){
                    divider.setDividerSize(splitPane.getDividerSize());
                 dividerSize = divider.getDividerSize();
                    splitPane.revalidate();
                 splitPane.repaint(); } } } }
    /**
     * Implementation of the FocusListener that the JSplitPane UI uses.
     * <p>
     * This inner class is marked &quot;public&quot; due to a compiler bug.
     * This class should be treated as a &quot;protected&quot; inner class.
     * Instantiate it only within subclasses of BasicSplitPaneUI.
     */
    public class FocusHandler extends FocusAdapter {
        public void focusGained(FocusEvent ev) {
            dividerKeyboardResize = true;
            splitPane.repaint(); }
        public void focusLost(FocusEvent ev) {
            dividerKeyboardResize = false;
            splitPane.repaint(); } }
    /**
     * Implementation of an ActionListener that the JSplitPane UI uses for
     * handling specific key presses.
     * <p>
     * This inner class is marked &quot;public&quot; due to a compiler bug.
     * This class should be treated as a &quot;protected&quot; inner class.
     * Instantiate it only within subclasses of BasicSplitPaneUI.
     */
    public class KeyboardUpLeftHandler implements ActionListener {
        public void actionPerformed(ActionEvent ev) {
            if (dividerKeyboardResize) {
             splitPane.setDividerLocation(Math.max(0,getDividerLocation
                        (splitPane) - getKeyboardMoveIncrement())); } } }
    static class KeyboardUpLeftAction extends AbstractAction {
        public void actionPerformed(ActionEvent ev) {
           JSplitPane splitPane = (JSplitPane)ev.getSource();
           BasicSplitPaneUI ui = (BasicSplitPaneUI)splitPane.getUI();
            if (ui.dividerKeyboardResize) {
             splitPane.setDividerLocation(Math.max(0,ui.getDividerLocation
                        (splitPane) - ui.getKeyboardMoveIncrement())); } } }
    /**
     * Implementation of an ActionListener that the JSplitPane UI uses for
     * handling specific key presses.
     * <p>
     * This inner class is marked &quot;public&quot; due to a compiler bug.
     * This class should be treated as a &quot;protected&quot; inner class.
     * Instantiate it only within subclasses of BasicSplitPaneUI.
     */
    public class KeyboardDownRightHandler implements ActionListener {
        public void actionPerformed(ActionEvent ev) {
            if (dividerKeyboardResize) {
                splitPane.setDividerLocation(getDividerLocation(splitPane) +
                              getKeyboardMoveIncrement()); } } }
    static class KeyboardDownRightAction extends AbstractAction {
        public void actionPerformed(ActionEvent ev) {
           JSplitPane splitPane = (JSplitPane)ev.getSource();
           BasicSplitPaneUI ui = (BasicSplitPaneUI)splitPane.getUI();
            if (ui.dividerKeyboardResize) {
                splitPane.setDividerLocation(ui.getDividerLocation(splitPane) +
                              ui.getKeyboardMoveIncrement()); } } }
    /**
     * Implementation of an ActionListener that the JSplitPane UI uses for
     * handling specific key presses.
     * <p>
     * This inner class is marked &quot;public&quot; due to a compiler bug.
     * This class should be treated as a &quot;protected&quot; inner class.
     * Instantiate it only within subclasses of BasicSplitPaneUI.
     */
    public class KeyboardHomeHandler implements ActionListener {
        public void actionPerformed(ActionEvent ev) {
            if (dividerKeyboardResize) {
                splitPane.setDividerLocation(0); } } }
    static class KeyboardHomeAction extends AbstractAction {
        public void actionPerformed(ActionEvent ev) {
           JSplitPane splitPane = (JSplitPane)ev.getSource();
           BasicSplitPaneUI ui = (BasicSplitPaneUI)splitPane.getUI();
            if (ui.dividerKeyboardResize) {
                splitPane.setDividerLocation(0); } } }
    /**
     * Implementation of an ActionListener that the JSplitPane UI uses for
     * handling specific key presses.
     * <p>
     * This inner class is marked &quot;public&quot; due to a compiler bug.
     * This class should be treated as a &quot;protected&quot; inner class.
     * Instantiate it only within subclasses of BasicSplitPaneUI.
     */
    public class KeyboardEndHandler implements ActionListener {
        public void actionPerformed(ActionEvent ev) {
            if (dividerKeyboardResize) {
             Insets   insets = splitPane.getInsets();
             int      bottomI = (insets != null) ? insets.bottom : 0;
             int      rightI = (insets != null) ? insets.right : 0;
                if (orientation == JSplitPane.VERTICAL_SPLIT) {
                    splitPane.setDividerLocation(splitPane.getHeight() -
                                       bottomI); }
                else {
                    splitPane.setDividerLocation(splitPane.getWidth() -
                            rightI); } } } }
    static class KeyboardEndAction extends AbstractAction {
        public void actionPerformed(ActionEvent ev) {
           JSplitPane splitPane = (JSplitPane)ev.getSource();
           BasicSplitPaneUI ui = (BasicSplitPaneUI)splitPane.getUI();
            if (ui.dividerKeyboardResize) {
             Insets   insets = splitPane.getInsets();
             int      bottomI = (insets != null) ? insets.bottom : 0;
             int      rightI = (insets != null) ? insets.right : 0;
                if (ui.orientation == JSplitPane.VERTICAL_SPLIT) {
                    splitPane.setDividerLocation(splitPane.getHeight() -
                                       bottomI); }
                else {
                    splitPane.setDividerLocation(splitPane.getWidth() -
                            rightI); } } } }
    /**
     * Implementation of an ActionListener that the JSplitPane UI uses for
     * handling specific key presses.
     * <p>
     * This inner class is marked &quot;public&quot; due to a compiler bug.
     * This class should be treated as a &quot;protected&quot; inner class.
     * Instantiate it only within subclasses of BasicSplitPaneUI.
     */
    public class KeyboardResizeToggleHandler implements ActionListener {
        public void actionPerformed(ActionEvent ev) {
            if (!dividerKeyboardResize) {
                splitPane.requestFocus();
                dividerKeyboardResize = true;
                splitPane.repaint(); } } }
    static class KeyboardResizeToggleAction extends AbstractAction {
        public void actionPerformed(ActionEvent ev) {
           JSplitPane splitPane = (JSplitPane)ev.getSource();
           BasicSplitPaneUI ui = (BasicSplitPaneUI)splitPane.getUI();
            if (!ui.dividerKeyboardResize) {
                splitPane.requestFocus();
                ui.dividerKeyboardResize = true;
                splitPane.repaint(); } } }
    /**
     * ActionListener that will focus on the opposite component that
     * has focus. EG if the left side has focus, this will transfer focus
     * to the right component.
     */
    static class ToggleSideFocusAction extends AbstractAction {
       public void actionPerformed(ActionEvent ae) {
           JSplitPane splitPane = (JSplitPane)ae.getSource();
           // Determine which side currently has focus. If there is only
           // one component, don't change the focus.
           Component        left = splitPane.getLeftComponent();
           Component        right = splitPane.getRightComponent();
           Component        focus = SwingUtilities.findFocusOwner(left);
           Component        focusOn;
           if (focus == null) {
             focusOn = SwingUtilities.findFocusOwner(right);
             if (focusOn != null) {
                 if (left != null) {
                  focusOn = left; }
                 else {
                  focusOn = null; } }
             else if (left != null) {
                 focusOn = left; }
             else if (right != null) {
                 focusOn = right; } }
           else if (right != null) {
             focusOn = right; }
           else {
             focusOn = left; }
           if (focusOn != null) {
             // Found the component to request focus on.
                focusOn.requestFocus(); } } }
    /**
     * Returns the divider between the top Components.
     */
    public BasicSplitPaneDivider getDivider() {
        return divider; }
    /**
     * Returns the default non continuous layout divider, which is an
     * instanceof Canvas that fills the background in dark gray.
     */
    protected Component createDefaultNonContinuousLayoutDivider() {
        return new Canvas() {
            public void paint(Graphics g) {
                if(!isContinuousLayout() && getLastDragLocation() != -1) {
                    Dimension      size = splitPane.getSize();
                    g.setColor(Color.darkGray);
                    if(orientation == JSplitPane.HORIZONTAL_SPLIT) {
                        g.fillRect(0, 0, dividerSize - 1, size.height - 1);
                    } else {
                        g.fillRect(0, 0, size.width - 1, dividerSize - 1); } } }
        }; }
    /**
     * Sets the divider to use when the splitPane is configured to
     * not continuously layout. This divider will only be used during a
     * dragging session. It is recommended that the passed in component
     * be a heavy weight.
     */
    protected void setNonContinuousLayoutDivider(Component newDivider) {
        setNonContinuousLayoutDivider(newDivider, true); }
    /**
     * Sets the divider to use.
     */
    protected void setNonContinuousLayoutDivider(Component newDivider,
        boolean rememberSizes) {
        rememberPaneSizes = rememberSizes;
        if(nonContinuousLayoutDivider != null && splitPane != null) {
            splitPane.remove(nonContinuousLayoutDivider); }
        nonContinuousLayoutDivider = newDivider; }
    private void addHeavyweightDivider() {
        if(nonContinuousLayoutDivider != null && splitPane != null) {
            /* Needs to remove all the components and re-add them! YECK! */
           // This is all done so that the nonContinuousLayoutDivider will
           // be drawn on top of the other components, without this, one
           // of the heavyweights will draw over the divider!
            Component             leftC = splitPane.getLeftComponent();
            Component             rightC = splitPane.getRightComponent();
           int                   lastLocation = splitPane.
                                           getDividerLocation();
            if(leftC != null)
                splitPane.setLeftComponent(null);
            if(rightC != null)
                splitPane.setRightComponent(null);
            splitPane.remove(divider);
            splitPane.add(nonContinuousLayoutDivider, BasicSplitPaneUI.
                          NON_CONTINUOUS_DIVIDER,
                          splitPane.getComponentCount());
            splitPane.setLeftComponent(leftC);
            splitPane.setRightComponent(rightC);
            splitPane.add(divider, JSplitPane.DIVIDER);
            if(rememberPaneSizes) {
             splitPane.setDividerLocation(lastLocation); } } }
    /**
     * Returns the divider to use when the splitPane is configured to
     * not continuously layout. This divider will only be used during a
     * dragging session.
     */
    public Component getNonContinuousLayoutDivider() {
        return nonContinuousLayoutDivider; }
    /**
     * Returns the splitpane this instance is currently contained
     * in.
     */
    public JSplitPane getSplitPane() {
        return splitPane; }
    /**
     * Creates the default divider.
     */
    public BasicSplitPaneDivider createDefaultDivider() {
        return new BasicSplitPaneDivider(this); }
    /**
     * Messaged to reset the preferred sizes.
     */
    public void resetToPreferredSizes(JSplitPane jc) {
        if(splitPane != null) {
            layoutManager.resetToPreferredSizes();
            splitPane.revalidate();
            layoutManager.layoutContainer(splitPane);
           splitPane.repaint(); } }
    /**
     * Sets the location of the divider to location.
     */
    public void setDividerLocation(JSplitPane jc, int location) {
       if (!ignoreDividerLocationChange) {
           dividerLocationIsSet = true;
           splitPane.revalidate();
           splitPane.repaint(); }
       else {
           ignoreDividerLocationChange = false; } }
    /**
     * Returns the location of the divider, which may differ from what
     * the splitpane thinks the location of the divider is.
     */
    public int getDividerLocation(JSplitPane jc) {
        if(orientation == JSplitPane.HORIZONTAL_SPLIT)
            return divider.getLocation().x;
        return divider.getLocation().y; }
    /**
     * Gets the minimum location of the divider.
     */
    public int getMinimumDividerLocation(JSplitPane jc) {
        int       minLoc = 0;
        Component leftC = splitPane.getLeftComponent();
        if ((leftC != null) && (leftC.isVisible())) {
            Insets    insets = splitPane.getInsets();
            Dimension minSize = leftC.getMinimumSize();
            if(orientation == JSplitPane.HORIZONTAL_SPLIT) {
                minLoc = minSize.width;
            } else {
                minLoc = minSize.height; }
            if(insets != null) {
                if(orientation == JSplitPane.HORIZONTAL_SPLIT) {
                    minLoc += insets.left;
                } else {
                    minLoc += insets.top; } } }
        return minLoc; }
    /**
     * Gets the maximum location of the divider.
     */
    public int getMaximumDividerLocation(JSplitPane jc) {
        Dimension splitPaneSize = splitPane.getSize();
        int       maxLoc = 0;
        Component rightC = splitPane.getRightComponent();
        if (rightC != null) {
            Insets    insets = splitPane.getInsets();
            Dimension minSize = new Dimension(0, 0);
            if (rightC.isVisible()) {
                minSize = rightC.getMinimumSize(); }
            if(orientation == JSplitPane.HORIZONTAL_SPLIT) {
                maxLoc = splitPaneSize.width - minSize.width;
            } else {
                maxLoc = splitPaneSize.height - minSize.height;  }
            maxLoc -= dividerSize;
            if(insets != null) {
                if(orientation == JSplitPane.HORIZONTAL_SPLIT) {
                    maxLoc -= insets.right;
                } else {
                    maxLoc -= insets.top; } } }
        return Math.max(getMinimumDividerLocation(splitPane), maxLoc); }
    /**
     * Messaged after the JSplitPane the receiver is providing the look
     * and feel for paints its children.
     */
    public void finishedPaintingChildren(JSplitPane jc, Graphics g) {
        if(jc == splitPane && getLastDragLocation() != -1 &&
           !isContinuousLayout() && !draggingHW) {
            Dimension      size = splitPane.getSize();
            g.setColor(Color.darkGray);
            if(orientation == JSplitPane.HORIZONTAL_SPLIT) {
                g.fillRect(getLastDragLocation(), 0, dividerSize - 1,
                           size.height - 1);
            } else {
                g.fillRect(0, lastDragLocation, size.width - 1,
                           dividerSize - 1); } } }
    /**
     * Messaged to paint the look and feel.
     */
    public void paint(Graphics g, JComponent jc) {
       painted = true; }
    /**
     * Returns the preferred size for the passed in component,
     * This is passed off to the current layoutmanager.
     */
    public Dimension getPreferredSize(JComponent jc) {
        if(splitPane != null)
            return layoutManager.preferredLayoutSize(splitPane);
        return new Dimension(0, 0); }
    /**
     * Returns the minimum size for the passed in component,
     * This is passed off to the current layoutmanager.
     */
    public Dimension getMinimumSize(JComponent jc) {
        if(splitPane != null)
            return layoutManager.minimumLayoutSize(splitPane);
        return new Dimension(0, 0); }
    /**
     * Returns the maximum size for the passed in component,
     * This is passed off to the current layoutmanager.
     */
    public Dimension getMaximumSize(JComponent jc) {
        if(splitPane != null)
            return layoutManager.maximumLayoutSize(splitPane);
        return new Dimension(0, 0); }
    /**
     * Returns the insets. The insets are returned from the border insets
     * of the current border.
     */
    public Insets getInsets(JComponent jc) {
        return null; }
    /**
     * Resets the layout manager based on orientation and messages it
     * with invalidateLayout to pull in appropriate Components.
     */
    protected void resetLayoutManager() {
        if(orientation == JSplitPane.HORIZONTAL_SPLIT) {
            layoutManager = new BasicHorizontalLayoutManager();
        } else {
            layoutManager = new BasicVerticalLayoutManager(); }
        splitPane.setLayout(layoutManager);
        layoutManager.updateComponents();
        splitPane.revalidate();
        splitPane.repaint(); }
    /**
     * Should be messaged before the dragging session starts, resets
     * lastDragLocation and dividerSize.
     */
    protected void startDragging() {
        Component       leftC = splitPane.getLeftComponent();
        Component       rightC = splitPane.getRightComponent();
        ComponentPeer   cPeer;
        beginDragDividerLocation = getDividerLocation(splitPane);
        draggingHW = false;
        if(leftC != null && (cPeer = leftC.getPeer()) != null &&
           !(cPeer instanceof LightweightPeer)) {
            draggingHW = true;
        } else if(rightC != null && (cPeer = rightC.getPeer()) != null
                  && !(cPeer instanceof LightweightPeer)) {
            draggingHW = true; }
        if(orientation == JSplitPane.HORIZONTAL_SPLIT) {
            setLastDragLocation(divider.getBounds().x);
            dividerSize = divider.getSize().width;
            if(!isContinuousLayout() && draggingHW) {
                nonContinuousLayoutDivider.setBounds
                        (getLastDragLocation(), 0, dividerSize,
                         splitPane.getHeight());
                   addHeavyweightDivider(); }
        } else {
            setLastDragLocation(divider.getBounds().y);
            dividerSize = divider.getSize().height;
            if(!isContinuousLayout() && draggingHW) {
                nonContinuousLayoutDivider.setBounds
                        (0, getLastDragLocation(), splitPane.getWidth(),
                         dividerSize);
                   addHeavyweightDivider(); } } }
    /**
     * Messaged during a dragging session to move the divider to the
     * passed in location. If continuousLayout is true the location is
     * reset and the splitPane validated.
     */
    protected void dragDividerTo(int location) {
        if(getLastDragLocation() != location) {
            if(isContinuousLayout()) {
             splitPane.setDividerLocation(location);
                setLastDragLocation(location);
            } else {
                int lastLoc = getLastDragLocation();
                setLastDragLocation(location);
                if(orientation == JSplitPane.HORIZONTAL_SPLIT) {
                    if(draggingHW) {
                        nonContinuousLayoutDivider.setLocation(
                            getLastDragLocation(), 0);
                    } else {
                  int   splitHeight = splitPane.getHeight();
                        splitPane.repaint(lastLoc, 0, dividerSize,
                                          splitHeight);
                        splitPane.repaint(location, 0, dividerSize,
                                          splitHeight); }
                } else {
                    if(draggingHW) {
                        nonContinuousLayoutDivider.setLocation(0,
                            getLastDragLocation());
                    } else {
                  int    splitWidth = splitPane.getWidth();
                        splitPane.repaint(0, lastLoc, splitWidth,
                                          dividerSize);
                        splitPane.repaint(0, location, splitWidth,
                                          dividerSize); } } } } }
    /**
     * Messaged to finish the dragging session. If not continuous display
     * the dividers location will be reset.
     */
    protected void finishDraggingTo(int location) {
        dragDividerTo(location);
        setLastDragLocation(-1);
        if(!isContinuousLayout()) {
            Component   leftC = splitPane.getLeftComponent();
            Rectangle   leftBounds = leftC.getBounds();
           if (draggingHW) {
             if(orientation == JSplitPane.HORIZONTAL_SPLIT) {
                    nonContinuousLayoutDivider.setLocation(-dividerSize, 0); }
             else {
                    nonContinuousLayoutDivider.setLocation(0, -dividerSize); }
             splitPane.remove(nonContinuousLayoutDivider); }
           splitPane.setDividerLocation(location); } }
    /**
     * As of Java 2 platform v1.3 this method is no longer used. Instead
     * you should set the border on the divider.
     * <p>
     * Returns the width of one side of the divider border.
     *
     * @deprecated As of Java 2 platform v1.3, instead set the border on the
     * divider.
     */
    protected int getDividerBorderSize() {
        return 1; }
    /**
     * LayoutManager for JSplitPanes that have an orientation of
     * HORIZONTAL_SPLIT.
     * <p>
     * This inner class is marked &quot;public&quot; due to a compiler bug.
     * This class should be treated as a &quot;protected&quot; inner class.
     * Instantiate it only within subclasses of BasicSplitPaneUI.
     */
    public class BasicHorizontalLayoutManager implements LayoutManager2 {
        /* left, right, divider. (in this exact order) */
        protected int[]         sizes;
        protected Component[]   components;
       /** Size of the splitpane the last time laid out. */
       private int             lastSplitPaneSize;
       /** True if resetToPreferredSizes has been invoked. */
       private boolean         doReset;
       /** Axis, 0 for horizontal, or 1 for veritcal. */
       private int             axis;
       BasicHorizontalLayoutManager() {
           this(0); }
        BasicHorizontalLayoutManager(int axis) {
           this.axis = axis;
            components = new Component[3];
            components[0] = components[1] = components[2] = null;
            sizes = new int[3]; }
       //
       // LayoutManager
       //
       /**
         * Does the actual layout.
         */
        public void layoutContainer(Container container) {
            Dimension   containerSize = container.getSize();
            // If the splitpane has a zero size then no op out of here.
            // If we execute this function now, we're going to cause ourselves
            // much grief.
            if (containerSize.height <= 0 || containerSize.width <= 0 ) {
             lastSplitPaneSize = 0;
                return; }
           int         spDividerLocation = splitPane.getDividerLocation();
            Insets      insets = splitPane.getInsets();
           int         availableSize = getAvailableSize(containerSize,
                             insets);
           int         newSize = getSizeForPrimaryAxis(containerSize);
           int         beginLocation = getDividerLocation(splitPane);
           int         dOffset = getSizeForPrimaryAxis(insets, true);
           Dimension   dSize = (components[2] == null) ? null :
                              components[2].getPreferredSize();
           if ((doReset && !dividerLocationIsSet) || spDividerLocation < 0) {
             resetToPreferredSizes(availableSize); }
           else if (lastSplitPaneSize <= 0 ||
                  availableSize == lastSplitPaneSize || !painted ||
                  (dSize != null &&
                   getSizeForPrimaryAxis(dSize) != sizes[2])) {
             if (dSize != null) {
                 sizes[2] = getSizeForPrimaryAxis(dSize); }
             else {
                 sizes[2] = 0; }
             setDividerLocation(spDividerLocation - dOffset, availableSize);
             dividerLocationIsSet = false; }
           else if (availableSize != lastSplitPaneSize) {
             distributeSpace(availableSize - lastSplitPaneSize, true); }
           doReset = false;
           dividerLocationIsSet = false;
           lastSplitPaneSize = availableSize;
            // Reset the bounds of each component
            int nextLocation = getInitialLocation(insets);
           int counter = 0;
            while (counter < 3) {
                if (components[counter] != null &&
                 components[counter].isVisible()) {
                    setComponentToSize(components[counter], sizes[counter],
                                       nextLocation, insets, containerSize);
                    nextLocation += sizes[counter]; }
                switch (counter) {
                case 0:
                    counter = 2;
                    break;
                case 2:
                    counter = 1;
                    break;
                case 1:
                    counter = 3;
                    break; } }
           if (painted) {
             // This is tricky, there is never a good time for us
             // to push the value to the splitpane, painted appears to
             // the best time to do it. What is really needed is
             // notification that layout has completed.
             int      newLocation = getDividerLocation(splitPane);
             if (newLocation != (spDividerLocation - dOffset)) {
                 int  lastLocation = splitPane.getLastDividerLocation();
                 ignoreDividerLocationChange = true;
                 try {
                  splitPane.setDividerLocation(newLocation);
                  // This is not always needed, but is rather tricky
                  // to determine when... The case this is needed for
                  // is if the user sets the divider location to some
                  // bogus value, say 0, and the actual value is 1, the
                  // call to setDividerLocation(1) will preserve the
                  // old value of 0, when we really want the divider
                  // location value  before the call. This is needed for
                  // the one touch buttons.
                  splitPane.setLastDividerLocation(lastLocation);
                 } finally {
                  ignoreDividerLocationChange = false; } } } }
        /**
         * Adds the component at place.  Place must be one of
         * JSplitPane.LEFT, RIGHT, TOP, BOTTOM, or null (for the
         * divider).
         */
        public void addLayoutComponent(String place, Component component) {
            boolean isValid = true;
            if(place != null) {
                if(place.equals(JSplitPane.DIVIDER)) {
                    /* Divider. */
                    components[2] = component;
                    sizes[2] = getSizeForPrimaryAxis(component.
                                getPreferredSize());
                } else if(place.equals(JSplitPane.LEFT) ||
                          place.equals(JSplitPane.TOP)) {
                    components[0] = component;
                    sizes[0] = 0;
                } else if(place.equals(JSplitPane.RIGHT) ||
                          place.equals(JSplitPane.BOTTOM)) {
                    components[1] = component;
                    sizes[1] = 0;
                } else if(!place.equals(
                                    BasicSplitPaneUI.NON_CONTINUOUS_DIVIDER))
                    isValid = false;
            } else {
                isValid = false; }
            if(!isValid)
                throw new IllegalArgumentException("cannot add to layout: " +
                    "unknown constraint: " +
                    place);
           doReset = true; }
        /**
         * Returns the minimum size needed to contain the children.
         * The width is the sum of all the childrens min widths and
         * the height is the largest of the childrens minimum heights.
         */
        public Dimension minimumLayoutSize(Container container) {
            int         minPrimary = 0;
            int         minSecondary = 0;
            Insets      insets = splitPane.getInsets();
            for (int counter=0; counter<3; counter++) {
                if(components[counter] != null) {
                    Dimension   minSize = components[counter].getMinimumSize();
                 int         secSize = getSizeForSecondaryAxis(minSize);
                    minPrimary += getSizeForPrimaryAxis(minSize);
                    if(secSize > minSecondary)
                        minSecondary = secSize; } }
            if(insets != null) {
                minPrimary += getSizeForPrimaryAxis(insets, true) +
                           getSizeForPrimaryAxis(insets, false);
             minSecondary += getSizeForSecondaryAxis(insets, true) +
                           getSizeForSecondaryAxis(insets, false); }
           if (axis == 0) {
             return new Dimension(minPrimary, minSecondary); }
           return new Dimension(minSecondary, minPrimary); }
        /**
         * Returns the preferred size needed to contain the children.
         * The width is the sum of all the childrens preferred widths and
         * the height is the largest of the childrens preferred heights.
         */
        public Dimension preferredLayoutSize(Container container) {
            int         prePrimary = 0;
            int         preSecondary = 0;
            Insets      insets = splitPane.getInsets();
            for(int counter = 0; counter < 3; counter++) {
                if(components[counter] != null) {
                 Dimension   preSize = components[counter].
                                    getPreferredSize();
                 int         secSize = getSizeForSecondaryAxis(preSize);
                    prePrimary += getSizeForPrimaryAxis(preSize);
                    if(secSize > preSecondary)
                        preSecondary = secSize; } }
            if(insets != null) {
                prePrimary += getSizeForPrimaryAxis(insets, true) +
                           getSizeForPrimaryAxis(insets, false);
             preSecondary += getSizeForSecondaryAxis(insets, true) +
                           getSizeForSecondaryAxis(insets, false); }
           if (axis == 0) {
             return new Dimension(prePrimary, preSecondary); }
           return new Dimension(preSecondary, prePrimary); }
        /**
         * Removes the specified component from our knowledge.
         */
        public void removeLayoutComponent(Component component) {
            for(int counter = 0; counter < 3; counter++) {
                if(components[counter] == component) {
                    components[counter] = null;
                    sizes[counter] = 0;
                 doReset = true; } } }
        //
        // LayoutManager2
        //
        /**
         * Adds the specified component to the layout, using the specified
         * constraint object.
         * @param comp the component to be added
         * @param constraints  where/how the component is added to the layout.
         */
        public void addLayoutComponent(Component comp, Object constraints) {
            if ((constraints == null) || (constraints instanceof String)) {
                addLayoutComponent((String)constraints, comp);
            } else {
                throw new IllegalArgumentException("cannot add to layout: " +
                                                   "constraint must be a " +
                                                   "string (or null)"); } }
        /**
         * Returns the alignment along the x axis.  This specifies how
         * the component would like to be aligned relative to other 
         * components.  The value should be a number between 0 and 1
         * where 0 represents alignment along the origin, 1 is aligned
         * the furthest away from the origin, 0.5 is centered, etc.
         */
        public float getLayoutAlignmentX(Container target) {
            return 0.0f; }
        /**
         * Returns the alignment along the y axis.  This specifies how
         * the component would like to be aligned relative to other 
         * components.  The value should be a number between 0 and 1
         * where 0 represents alignment along the origin, 1 is aligned
         * the furthest away from the origin, 0.5 is centered, etc.
         */
        public float getLayoutAlignmentY(Container target) {
            return 0.0f; }
        /**
         * Does nothing. If the developer really wants to change the
         * size of one of the views JSplitPane.resetToPreferredSizes should
         * be messaged.
         */
        public void invalidateLayout(Container c) { }
        /**
         * Returns the maximum layout size, which is Integer.MAX_VALUE
         * in both directions.
         */
        public Dimension maximumLayoutSize(Container target) {
            return new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE); }
       //
       // New methods.
       //
        /**
         * Marks the receiver so that the next time this instance is
         * laid out it'll ask for the preferred sizes.
         */
        public void resetToPreferredSizes() {
           doReset = true; }
        /**
         * Resets the size of the Component at the passed in location.
         */
        protected void resetSizeAt(int index) {
            sizes[index] = 0;
           doReset = true; }
        /**
         * Sets the sizes to <code>newSizes</code>.
         */
        protected void setSizes(int[] newSizes) {
            System.arraycopy(newSizes, 0, sizes, 0, 3); }
        /**
         * Returns the sizes of the components.
         */
        protected int[] getSizes() {
            int[]         retSizes = new int[3];
            System.arraycopy(sizes, 0, retSizes, 0, 3);
            return retSizes; }
        /**
         * Returns the width of the passed in Components preferred size.
         */
        protected int getPreferredSizeOfComponent(Component c) {
           return getSizeForPrimaryAxis(c.getPreferredSize()); }
        /**
         * Returns the width of the passed in Components minimum size.
         */
        int getMinimumSizeOfComponent(Component c) {
           return getSizeForPrimaryAxis(c.getMinimumSize()); }
        /**
         * Returns the width of the passed in component.
         */
        protected int getSizeOfComponent(Component c) {
           return getSizeForPrimaryAxis(c.getSize()); }
        /**
         * Returns the available width based on the container size and
         * Insets.
         */
        protected int getAvailableSize(Dimension containerSize,
                                       Insets insets) {
            if(insets == null)
                return getSizeForPrimaryAxis(containerSize);
            return (getSizeForPrimaryAxis(containerSize) - 
                 (getSizeForPrimaryAxis(insets, true) +
                  getSizeForPrimaryAxis(insets, false))); }
        /**
         * Returns the left inset, unless the Insets are null in which case
         * 0 is returned.
         */
        protected int getInitialLocation(Insets insets) {
            if(insets != null)
                return getSizeForPrimaryAxis(insets, true);
            return 0; }
        /**
         * Sets the width of the component c to be size, placing its
         * x location at location, y to the insets.top and height
         * to the containersize.height less the top and bottom insets.
         */
        protected void setComponentToSize(Component c, int size,
                                          int location, Insets insets,
                                          Dimension containerSize) {
            if(insets != null) {
             if (axis == 0) {
                 c.setBounds(location, insets.top, size,
                      containerSize.height -
                      (insets.top + insets.bottom)); }
             else {
                 c.setBounds(insets.left, location, containerSize.width -
                      (insets.left + insets.right), size); } }
           else {
                if (axis == 0) {
                 c.setBounds(location, 0, size, containerSize.height); }
             else {
                 c.setBounds(0, location, containerSize.width, size); } } }
       /**
        * If the axis == 0, the width is returned, otherwise the height.
        */
       int getSizeForPrimaryAxis(Dimension size) {
           if (axis == 0) {
             return size.width; }
           return size.height; }
       /**
        * If the axis == 0, the width is returned, otherwise the height.
        */
       int getSizeForSecondaryAxis(Dimension size) {
           if (axis == 0) {
             return size.height; }
           return size.width; }
       /**
        * Returns a particular value of the inset identified by the
        * axis and <code>isTop</code><p>
        *   axis isTop
        *    0    true    - left
        *    0    false   - right
        *    1    true    - top
        *    1    false   - bottom
        */
       int getSizeForPrimaryAxis(Insets insets, boolean isTop) {
           if (axis == 0) {
             if (isTop) {
                 return insets.left; }
             return insets.right; }
           if (isTop) {
             return insets.top; }
           return insets.bottom; }
       /**
        * Returns a particular value of the inset identified by the
        * axis and <code>isTop</code><p>
        *   axis isTop
        *    0    true    - left
        *    0    false   - right
        *    1    true    - top
        *    1    false   - bottom
        */
       int getSizeForSecondaryAxis(Insets insets, boolean isTop) {
           if (axis == 0) {
             if (isTop) {
                 return insets.top; }
             return insets.bottom; }
           if (isTop) {
             return insets.left; }
           return insets.right; }
        /**
         * Determines the components. This should be called whenever
         * a new instance of this is installed into an existing
         * SplitPane.
         */
        protected void updateComponents() {
            Component comp;
            comp = splitPane.getLeftComponent();
            if(components[0] != comp) {
                components[0] = comp;
                if(comp == null) {
                    sizes[0] = 0;
                } else {
                    sizes[0] = -1; } }
            comp = splitPane.getRightComponent();
            if(components[1] != comp) {
                components[1] = comp;
                if(comp == null) {
                    sizes[1] = 0;
                } else {
                    sizes[1] = -1; } }
            /* Find the divider. */
            Component[] children = splitPane.getComponents();
            Component   oldDivider = components[2];
            components[2] = null;
            for(int counter = children.length - 1; counter >= 0; counter--) {
                if(children[counter] != components[0] &&
                   children[counter] != components[1] &&
                   children[counter] != nonContinuousLayoutDivider) {
                    if(oldDivider != children[counter]) {
                        components[2] = children[counter];
                    } else {
                        components[2] = oldDivider; }
                    break; } }
            if(components[2] == null) {
             sizes[2] = 0; }
           else {
             sizes[2] = getSizeForPrimaryAxis(splitPane.getPreferredSize()); } }
       /**
        * Resets the size of the first component to <code>leftSize</code>,
        * and the right component to the remainder of the space.
        */
       void setDividerLocation(int leftSize, int availableSize) {
           boolean          lValid = (components[0] != null &&
                             components[0].isVisible());
           boolean          rValid = (components[1] != null &&
                             components[1].isVisible());
           boolean          dValid = (components[2] != null && 
                             components[2].isVisible());
           int              max = availableSize;
           if (dValid) {
             max -= sizes[2]; }
           leftSize = Math.max(0, Math.min(leftSize, max));
           if (lValid) {
             if (rValid) {
                 sizes[0] = leftSize;
                 sizes[1] = max - leftSize; }
             else {
                 sizes[0] = max;
                 sizes[1] = 0; } }
           else if (rValid) {
             sizes[1] = max;
             sizes[0] = 0; } }
       /**
        * Returns an array of the minimum sizes of the components.
        */
       int[] getPreferredSizes() {
           int[]         retValue = new int[3];
           for (int counter = 0; counter < 3; counter++) {
             if (components[counter] != null &&
                 components[counter].isVisible()) {
                 retValue[counter] = getPreferredSizeOfComponent
                                  (components[counter]); }
             else {
                 retValue[counter] = -1; } }
           return retValue; }
       /**
        * Returns an array of the minimum sizes of the components.
        */
       int[] getMinimumSizes() {
           int[]         retValue = new int[3];
           for (int counter = 0; counter < 2; counter++) {
             if (components[counter] != null &&
                 components[counter].isVisible()) {
                 retValue[counter] = getMinimumSizeOfComponent
                                  (components[counter]); }
             else {
                 retValue[counter] = -1; } }
           retValue[2] = (components[2] != null) ? sizes[2] : -1;
           return retValue; }
       /**
        * Resets the components to their preferred sizes.
        */
       void resetToPreferredSizes(int availableSize) {
           // Set the sizes to the preferred sizes (if fits), otherwise
           // set to min sizes and distribute any extra space.
           int[]       testSizes = getPreferredSizes();
           int         totalSize = 0;
           for (int counter = 0; counter < 3; counter++) {
             if (testSizes[counter] != -1) {
                 totalSize += testSizes[counter]; } }
           if (totalSize > availableSize) {
             testSizes = getMinimumSizes();
             totalSize = 0;
             for (int counter = 0; counter < 3; counter++) {
                 if (testSizes[counter] != -1) {
                  totalSize += testSizes[counter]; } } }
           setSizes(testSizes);
           distributeSpace(availableSize - totalSize, false); }
       /**
        * Distributes <code>space</code> between the two components 
        * (divider won't get any extra space) based on the weighting. This
        * attempts to honor the min size of the components.
         *
         * @param keepHidden if true and one of the components is 0x0
         *                   it gets none of the extra space
        */
       void distributeSpace(int space, boolean keepHidden) {
           boolean          lValid = (components[0] != null &&
                             components[0].isVisible());
           boolean          rValid = (components[1] != null &&
                             components[1].isVisible());
            if (keepHidden) {
                if (lValid && getSizeForPrimaryAxis(
                                 components[0].getSize()) == 0) {
                    lValid = false;
                    if (rValid && getSizeForPrimaryAxis(
                                     components[1].getSize()) == 0) {
                        // Both aren't valid, force them both to be valid
                        lValid = true; } }
                else if (rValid && getSizeForPrimaryAxis(
                                   components[1].getSize()) == 0) {
                    rValid = false; } }
           if (lValid && rValid) {
             double        weight = splitPane.getResizeWeight();
             int           lExtra = (int)(weight * (double)space);
             int           rExtra = (space - lExtra);
             sizes[0] += lExtra;
             sizes[1] += rExtra;
             int           lMin = getMinimumSizeOfComponent(components[0]);
             int           rMin = getMinimumSizeOfComponent(components[1]);
             boolean       lMinValid = (sizes[0] >= lMin);
             boolean       rMinValid = (sizes[1] >= rMin);
             if (!lMinValid && !rMinValid) {
                 if (sizes[0] < 0) {
                  sizes[1] += sizes[0];
                  sizes[0] = 0; }
                 else if (sizes[1] < 0) {
                  sizes[0] += sizes[1];
                  sizes[1] = 0; } }
             else if (!lMinValid) {
                 if (sizes[1] - (lMin - sizes[0]) < rMin) {
                  // both below min, just make sure > 0
                  if (sizes[0] < 0) {
                      sizes[1] += sizes[0];
                      sizes[0] = 0; } }
                 else {
                  sizes[1] -= (lMin - sizes[0]);
                  sizes[0] = lMin; } }
             else if (!rMinValid) {
                 if (sizes[0] - (rMin - sizes[1]) < lMin) {
                  // both below min, just make sure > 0
                  if (sizes[1] < 0) {
                      sizes[0] += sizes[1];
                      sizes[1] = 0; } }
                 else {
                  sizes[0] -= (rMin - sizes[1]);
                  sizes[1] = rMin; } }
             if (sizes[0] < 0) {
                 sizes[0] = 0; }
             if (sizes[1] < 0) {
                 sizes[1] = 0; } }
           else if (lValid) {
             sizes[0] = Math.max(0, sizes[0] + space); }
           else if (rValid) {
             sizes[1] = Math.max(0, sizes[1] + space); } } }
    /**
     * LayoutManager used for JSplitPanes with an orientation of
     * VERTICAL_SPLIT.
     * <p>
     * This inner class is marked &quot;public&quot; due to a compiler bug.
     * This class should be treated as a &quot;protected&quot; inner class.
     * Instantiate it only within subclasses of BasicSplitPaneUI.
     */
    public class BasicVerticalLayoutManager extends
            BasicHorizontalLayoutManager {
       public BasicVerticalLayoutManager() {
           super(1); } } }
