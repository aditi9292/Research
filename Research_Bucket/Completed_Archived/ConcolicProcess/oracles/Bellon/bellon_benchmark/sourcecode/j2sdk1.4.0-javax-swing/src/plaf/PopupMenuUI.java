/*
 * @(#)PopupMenuUI.java        1.14 01/12/03
 *
 * Copyright 2002 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package javax.swing.plaf;
import java.awt.event.MouseEvent;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.JPopupMenu;
/**
 * Pluggable look and feel interface for JPopupMenu.
 *
 * @version 1.14 12/03/01
 * @author Georges Saab
 * @author David Karlton
 */
public abstract class PopupMenuUI extends ComponentUI {
    /** 
     * @since 1.3
     */
    public boolean isPopupTrigger(MouseEvent e) {
       return e.isPopupTrigger(); }
    /**
     * Returns the <code>Popup</code> that will be responsible for
     * displaying the <code>JPopupMenu</code>.
     *
     * @param popup JPopupMenu requesting Popup
     * @param x     Screen x location Popup is to be shown at
     * @param y     Screen y location Popup is to be shown at.
     * @return Popup that will show the JPopupMenu
     * @since 1.4
     */
    public Popup getPopup(JPopupMenu popup, int x, int y) {
        PopupFactory popupFactory = PopupFactory.getSharedInstance();
        return popupFactory.getPopup(popup.getInvoker(), popup, x, y); } }
