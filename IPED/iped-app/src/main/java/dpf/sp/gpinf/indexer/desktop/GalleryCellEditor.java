/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer.desktop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.table.TableCellEditor;

import dpf.sp.gpinf.indexer.util.GalleryValue;
import dpf.sp.gpinf.indexer.util.ImageUtil;

public class GalleryCellEditor extends AbstractCellEditor implements TableCellEditor, ActionListener {

    private static final long serialVersionUID = 1L;

    int row, col;
    JPanel top = new JPanel(), panel = new JPanel();
    // JLayeredPane panel = new JLayeredPane();
    JLabel label = new JLabel(), cLabel = new JLabel();
    JCheckBox check = new JCheckBox();
    Border selBorder = BorderFactory.createLineBorder(new Color(50, 50, 100), 1, false);

    public GalleryCellEditor() {
        super();
        panel.setLayout(new BorderLayout());
        top.setLayout(new BorderLayout());
        top.add(check, BorderLayout.LINE_START);
        top.add(cLabel, BorderLayout.CENTER);
        panel.add(top, BorderLayout.NORTH);
        panel.add(label, BorderLayout.CENTER);

        label.setHorizontalAlignment(JLabel.CENTER);
        check.addActionListener(this);
    }

    @Override
    public Object getCellEditorValue() {
        return new JPanel();
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int col) {

        table.putClientProperty("terminateEditOnFocusLost", true); //$NON-NLS-1$

        GalleryValue cellValue = (GalleryValue) value;
        if (cellValue.id == null) {
            return new JPanel();
        }

        check.setSelected(App.get().appCase.getMultiMarcadores().isSelected(cellValue.id));
        cLabel.setText(cellValue.name);

        if (cellValue.icon == null && cellValue.image == null) {
            label.setText("..."); //$NON-NLS-1$
            label.setIcon(null);
        } else {
            label.setText(null);
            if (cellValue.image != null) {
                int labelW = table.getWidth() / table.getColumnCount() - 2;
                int labelH = GalleryCellRenderer.labelH;
                BufferedImage image = cellValue.image;
                int w = Math.min(cellValue.originalW, labelW);
                int h = Math.min(cellValue.originalH, labelH);
                image = ImageUtil.resizeImage(image, w, h);

                label.setIcon(new ImageIcon(image));
            } else {
                label.setIcon(cellValue.icon);
            }
        }

        panel.setBackground(new Color(180, 200, 230));
        top.setBackground(new Color(180, 200, 230));
        panel.setBorder(selBorder);
        this.row = row;
        this.col = col;

        return panel;
    }

    @Override
    public void actionPerformed(ActionEvent evt) {

        if (evt.getSource() == check) {
            int idx = row * App.get().galleryModel.colCount + col;
            App.get().resultsTable.setValueAt(check.isSelected(), idx, 1);
        }

        this.stopCellEditing();

    }

}
