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
package edu.upenn.cis.orchestra;

/**
 * Combines an {@code OrchestraTestFrame} with some kind of object which can be
 * used to control an Orchestra instance.
 * 
 * @author John Frommeyer
 * @param <T> the type of the Orchestra controller
 * 
 */
public interface ITestFrameWrapper<T> {

	/**
	 * Returns the wrapped {@code OrchestraTestFrame}.
	 * 
	 * @return the wrapped {@code OrchestraTestFrame}
	 */
	public OrchestraTestFrame getTestFrame();

	/**
	 * Returns the {@code T} which is used to control the associated Orchestra
	 * object.
	 * 
	 * @return the {@code T} which is used to control the associated Orchestra
	 *         object
	 */
	public T getOrchestraController();
}
