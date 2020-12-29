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
package info.bioinfweb.jphyloio.formats.xml;


import java.io.Reader;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import info.bioinfweb.jphyloio.ReadWriteParameterMap;
import info.bioinfweb.jphyloio.factory.AbstractSingleReaderWriterFactory;



/**
 * Implements shared functionality for reader and writer factories of XML formats.
 * 
 * @author Ben St&ouml;ver
 * @since 0.0.0
 */
public abstract class AbstractXMLFactory extends AbstractSingleReaderWriterFactory {
	private QName rootTag;
	
	
	/**
	 * Creates a new instance of this class.
	 * 
	 * @param rootTag the root tag expected in the target format (which is used in 
	 *        {@link #checkFormat(Reader, ReadWriteParameterMap)})
	 */
	public AbstractXMLFactory(QName rootTag) {
		super();
		this.rootTag = rootTag;
	}


	/**
	 * Returns the root tag that was specified in the constructor.
	 * 
	 * @return the expected root tag of the target format
	 */
	protected QName getRootTag() {
		return rootTag;
	}


	/**
	 * Implementations can overwrite this method in order to perform further tests on the root tag of the XML document.
	 * It is only called from within the {@code checkFormat()} methods, if the document is a XML document and the local
	 * part of the root tag was equal to the local part of {@link #getRootTag()}.
	 * <p>
	 * This abstract implementation always returns {@code true}.
	 * 
	 * @param startElement the event produced by the first start tag in the document
	 * @return {@code true} if the start element is valid for the target format or {@code false} otherwise
	 */
	protected boolean checkRootTag(StartElement startElement) {
		return true;
	}
	
	
	/**
	 * Tests whether the specified reader provides an XML document and whether the local part of the start tag of it
	 * equals to the local part of {@link #getRootTag()}.
	 * <p>
	 * For additional tests, {@link #checkRootTag(StartElement)} can overwritten by inherited classes. Note that this
	 * class does not test of the namespace of {@link #getRootTag()} matches that of the document root tag, to allow
	 * reading files without namespace definition (e.g. generated by third parts scripts). If an inherited class is
	 * associated with a reader that relies on finding a certain namespace, the namespace should additionally be 
	 * tested in {@link #checkRootTag(StartElement)}.
	 */
	@Override
	public boolean checkFormat(Reader reader, ReadWriteParameterMap parameters)	{
		try {
			XMLEventReader xmlReader = XMLInputFactory.newInstance().createXMLEventReader(reader);  //TODO Why is the underlying stream of the BufferedInputStream set to null here on some systems?
			
			if (!(xmlReader.nextEvent().getEventType() == XMLStreamConstants.START_DOCUMENT)) {
				return false;
			}
			
			XMLEvent event;
			do {  // Skip e.g. comments before root tag.
				event = xmlReader.nextEvent();
				if (event.isEndElement() || event.isEndDocument()) {
					return false;
				}
			} while (!event.isStartElement());
			
			StartElement startElement = event.asStartElement();
			if (startElement.getName().getLocalPart().equals(getRootTag().getLocalPart())) {
				return checkRootTag(startElement);
			}
			else {
				return false;
			}
		}
		catch (XMLStreamException e) {
			return false;
		}
	}
}