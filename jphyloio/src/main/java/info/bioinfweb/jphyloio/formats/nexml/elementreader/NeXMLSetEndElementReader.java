/*
 * JPhyloIO - Event based parsing and stream writing of multiple sequence alignment and tree formats. 
 * Copyright (C) 2015-2019  Ben Stöver, Sarah Wiechers
 * <http://bioinfweb.info/JPhyloIO>
 * 
 * This file is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This file is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package info.bioinfweb.jphyloio.formats.nexml.elementreader;


import info.bioinfweb.jphyloio.events.PartEndEvent;
import info.bioinfweb.jphyloio.events.type.EventContentType;
import info.bioinfweb.jphyloio.formats.nexml.NeXMLReaderStreamDataProvider;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;



/**
 * Abstract element reader that processes end elements of NeXML set tags.
 * 
 * @author Sarah Wiechers
 * @since 0.0.0
 */
public class NeXMLSetEndElementReader extends AbstractNeXMLElementReader {
	private EventContentType setType;
	
	
	public NeXMLSetEndElementReader(EventContentType setType) {
		super();
		this.setType = setType;
	}


	@Override
	public void readEvent(NeXMLReaderStreamDataProvider streamDataProvider,	XMLEvent event) throws IOException, XMLStreamException {
		streamDataProvider.getCurrentEventCollection().add(new PartEndEvent(setType, true));
		streamDataProvider.setCurrentSetIsSupported(false);
	}
}
