/*
 * @(#)TableColumnModelEvent.java      1.14 01/12/03
 *
 * Copyright 2002 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package javax.swing.event;
import java.util.EventObject;
import javax.swing.table.*;
/**
 * <B>TableColumnModelEvent</B> is used to notify listeners that a table
 * column model has changed, such as a column was added, removed, or
 * moved.
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
 * @version 1.14 12/03/01
 * @author Alan Chung
 * @see TableColumnModelListener
 */
public class TableColumnModelEvent extends java.util.EventObject {
    /** The index of the column from where it was moved or removed */
    protected int      fromIndex;
    /** The index of the column to where it was moved or added from */
    protected int      toIndex;
    /**
     * Constructs a TableColumnModelEvent object.
     *
     * @param source  the TableColumnModel that originated the event
     *                (typically <code>this</code>)
     * @param from    an int specifying the first row in a range of affected rows
     * @param to      an int specifying the last row in a range of affected rows
     */
    public TableColumnModelEvent(TableColumnModel source, int from, int to) {
       super(source);
       fromIndex = from;
       toIndex = to; }
    /** Returns the fromIndex.  Valid for removed or moved events */
    public int getFromIndex() { return fromIndex; };
    /** Returns the toIndex.  Valid for add and moved events */
    public int getToIndex() { return toIndex; }; }