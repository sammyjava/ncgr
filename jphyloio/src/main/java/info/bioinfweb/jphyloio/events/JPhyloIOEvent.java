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
package info.bioinfweb.jphyloio.events;


import info.bioinfweb.jphyloio.JPhyloIOEventReader;
import info.bioinfweb.jphyloio.events.meta.LiteralMetadataContentEvent;
import info.bioinfweb.jphyloio.events.meta.LiteralMetadataEvent;
import info.bioinfweb.jphyloio.events.meta.ResourceMetadataEvent;
import info.bioinfweb.jphyloio.events.type.EventContentType;
import info.bioinfweb.jphyloio.events.type.EventTopologyType;
import info.bioinfweb.jphyloio.events.type.EventType;



/**
 * This interface is implemented by all events generated by implementations of {@link JPhyloIOEventReader}.
 * <p>
 * Events in <i>JPhyloIO</i> are generated by implementations of {@link JPhyloIOEventReader} in an order according
 * to the grammar specified in the documentation of that class. The type of data that is represented by an event
 * is determined by its {@link EventContentType}. For some content types a start and an end event exist to allow
 * the sequential representation of nested information (e.g. the sequences of an alignment) and for others only
 * one sole event will be generated. This is defined by the {@link EventTopologyType}. Both types can be determined
 * using {@link #getType()}.
 * 
 * @author Ben St&ouml;ver
 */
public interface JPhyloIOEvent {
	/**
	 * Returns the type of this event. 
	 * 
	 * @return the event type
	 */
	public EventType getType();
	
	/**
	 * Casts this event to a resource metadata event.
	 * 
	 * @return a reference to this event as a resource metadata event
	 * @throws ClassCastException if this event is not an instance of {@link ResourceMetadataEvent}
	 */
	public ResourceMetadataEvent asResourceMetadataEvent() throws ClassCastException;
	
	/**
	 * Casts this event to a literal metadata event.
	 * 
	 * @return a reference to this event as a literal metadata event
	 * @throws ClassCastException if this event is not an instance of {@link LiteralMetadataEvent}
	 */
	public LiteralMetadataEvent asLiteralMetadataEvent() throws ClassCastException;
	
	/**
	 * Casts this event to a literal content metadata event.
	 * 
	 * @return a reference to this event as a literal content metadata event
	 * @throws ClassCastException if this event is not an instance of {@link LiteralMetadataContentEvent}
	 */
	public LiteralMetadataContentEvent asLiteralMetadataContentEvent() throws ClassCastException;
	
	/**
	 * Casts this event to an unknown command event.
	 * 
	 * @return a reference to this event as an unknown command event
	 * @throws ClassCastException if this event is not an instance of {@link UnknownCommandEvent}
	 */
	public UnknownCommandEvent asUnknownCommandEvent() throws ClassCastException;
	
	/**
	 * Casts this event to a comment event.
	 * 
	 * @return a reference to this event as a tokens event
	 * @throws ClassCastException if this event is not an instance of {@link CommentEvent}
	 */
	public CommentEvent asCommentEvent() throws ClassCastException;
	
	/**
	 * Casts this event to a labeled ID event.
	 * 
	 * @return a reference to this event as a labeled ID event
	 * @throws ClassCastException if this event is not an instance of {@link LabeledIDEvent}
	 */
	public LabeledIDEvent asLabeledIDEvent() throws ClassCastException;
	
	/**
	 * Casts this event to a set element event.
	 * 
	 * @return a reference to this event as a set element event
	 * @throws ClassCastException if this event is not an instance of {@link SetElementEvent}
	 */
	public SetElementEvent asSetElementEvent() throws ClassCastException;
	
	/**
	 * Casts this event to a part end event.
	 * 
	 * @return a reference to this event as a part end event
	 * @throws ClassCastException if this event is not an instance of {@link PartEndEvent}
	 */
	public PartEndEvent asPartEndEvent() throws ClassCastException;
	
	/**
	 * Casts this event to a basic OTU event.
	 * 
	 * @return a reference to this event as a basic OTU event
	 * @throws ClassCastException if this event is not an instance of {@link LinkedLabeledIDEvent}
	 */
	public LinkedLabeledIDEvent asLinkedLabeledIDEvent() throws ClassCastException;
	
	/**
	 * Casts this event to a tokens event.
	 * 
	 * @return a reference to this event as a tokens event
	 * @throws ClassCastException if this event is not an instance of {@link SequenceTokensEvent}
	 */
	public SequenceTokensEvent asSequenceTokensEvent() throws ClassCastException;
	
	/**
	 * Casts this event to a single token event.
	 * 
	 * @return a reference to this event as a single token event
	 * @throws ClassCastException if this event is not an instance of {@link SingleSequenceTokenEvent}
	 */
	public SingleSequenceTokenEvent asSingleSequenceTokenEvent() throws ClassCastException;
	
	/**
	 * Casts this event to a character set event.
	 * 
	 * @return a reference to this event as a tokens event
	 * @throws ClassCastException if this event is not an instance of {@link CharacterSetIntervalEvent}
	 */
	public CharacterSetIntervalEvent asCharacterSetIntervalEvent() throws ClassCastException;
	
	/**
	 * Casts this event to a character definition event.
	 * 
	 * @return a reference to this event as a tokens event
	 * @throws ClassCastException if this event is not an instance of {@link CharacterDefinitionEvent}
	 */
	public CharacterDefinitionEvent asCharacterDefinitionEvent() throws ClassCastException;
	
	/**
	 * Casts this event to a character state set definition event.
	 * 
	 * @return a reference to this event as a token set definition event
	 * @throws ClassCastException if this event is not an instance of {@link TokenSetDefinitionEvent}
	 */
	public TokenSetDefinitionEvent asTokenSetDefinitionEvent() throws ClassCastException;	
	
	/**
	 * Casts this event to a single character state symbol definition event.
	 * 
	 * @return a reference to this event as a token definition event
	 * @throws ClassCastException if this event is not an instance of {@link SingleTokenDefinitionEvent}
	 */
	public SingleTokenDefinitionEvent asSingleTokenDefinitionEvent() throws ClassCastException;	
	
	/**
	 * Casts this event to a tree or graph edge event.
	 * 
	 * @return a reference to this event as an edge event
	 * @throws ClassCastException if this event is not an instance of {@link EdgeEvent}
	 */
	public EdgeEvent asEdgeEvent() throws ClassCastException;
	
	/**
	 * Casts this event to a tree or graph node event.
	 * 
	 * @return a reference to this event as a node event
	 * @throws ClassCastException if this event is not an instance of {@link NodeEvent}
	 */
	public NodeEvent asNodeEvent() throws ClassCastException;
	
	/**
	 * Casts this event to an event with a linked ID.
	 * 
	 * @return a reference to this event as a node event
	 * @throws ClassCastException if this event is not an instance of {@link LinkedIDEvent}
	 */
	public LinkedIDEvent asLinkedIDEvent() throws ClassCastException;
}