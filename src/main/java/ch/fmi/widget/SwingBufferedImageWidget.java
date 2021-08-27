/*-
 * #%L
 * ImageJ utilities and commands for stitching various datasets
 * %%
 * Copyright (C) 2019 - 2021 Friedrich Miescher Institute for Biomedical
 * 			Research, Basel
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

package ch.fmi.widget;

import java.awt.image.BufferedImage;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.scijava.plugin.Plugin;
import org.scijava.ui.swing.widget.SwingInputWidget;
import org.scijava.widget.InputWidget;
import org.scijava.widget.WidgetModel;

@Plugin(type = InputWidget.class)
public class SwingBufferedImageWidget extends SwingInputWidget<BufferedImage>
	implements BufferedImageWidget<JPanel>
{

	private BufferedImage image;
	private JLabel preview;

	@Override
	public boolean supports(final WidgetModel model) {
		return super.supports(model) && model.isType(BufferedImage.class);
	}

	@Override
	public void set(final WidgetModel model) {
		super.set(model);

		getComponent().setLayout(new BoxLayout(getComponent(), BoxLayout.X_AXIS));

		preview = new JLabel();
		setToolTip(preview);
		getComponent().add(preview);

		refreshWidget();
	}

	@Override
	public BufferedImage getValue() {
		// Not intended to store any value
		return image;
	}

	@Override
	protected void doRefresh() {
		image = (BufferedImage) get().getValue();
		final ImageIcon icon = new ImageIcon(image);
		preview.setIcon(icon);
	}

}
