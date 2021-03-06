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
package info.bioinfweb.jphyloio.formats.nexus.blockhandlers;


import info.bioinfweb.jphyloio.formats.nexus.commandreaders.trees.ENewickNetworkReader;



/**
 * A <i>Nexus</i> block handler to read phylogenetic networks from a {@code NETWORK} block containing network definitions
 * in the <a href="http://dx.doi.org/10.1186/1471-2105-9-532">eNewick</i> format.
 * <p>
 * Note that such blocks are custom blocks, which are not part of the initial <i>Nexus</i> standard. Other blocks (e.g. the
 * {@code NETWORKS} block generated by <a href="http://ab.inf.uni-tuebingen.de/software/splitstree4">SplitsTree</i>) with the 
 * same name exist, that cannot by handled by instances of this class.  
 * 
 * @author Ben St&ouml;ver
 * @since 0.4.0
 * @see ENewickNetworkReader
 */
public class ENewickNetworksBlockHandler extends TreesBlockHandler {
	public ENewickNetworksBlockHandler() {
		super(new String[]{BLOCK_NAME_NETWORKS});
	}

	
	@Override
	protected String getStartTriggerCommand() {
		return COMMAND_NAME_NETWORK;
	}

	
	@Override
	protected String getBlockName() {
		return BLOCK_NAME_NETWORKS;
	}
}
