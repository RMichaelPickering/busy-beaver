/*
 * Copyright (C) 2010 Trustees of the University of Pennsylvania
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS of ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.upenn.cis.orchestra.gui.peers.graph;

import edu.upenn.cis.orchestra.datamodel.Peer;
import edu.upenn.cis.orchestra.datamodel.Schema;

public class EncapsPeerSchema 
{
	private Peer _p;
	private Schema _s;
	
	public EncapsPeerSchema (Peer p, Schema s)
	{
		_p = p;
		_s = s;
	}
	
	public Peer getPeer ()
	{
		return _p;			
	}
	
	public Schema getSchema ()
	{
		return _s;
	}
	
}