/*******************************************************************************
 * Copyright (c) 2011, Nathan Sweet <nathan.sweet@gmail.com>
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/

package com.esotericsoftware.jsonbeans;

/** Indicates an error during serialization due to misconfiguration or during deserialization due to invalid input data.
 * @author Nathan Sweet <misc@n4te.com> */
public class SerializationException extends RuntimeException {
	private StringBuffer trace;

	public SerializationException () {
		super();
	}

	public SerializationException (String message, Throwable cause) {
		super(message, cause);
	}

	public SerializationException (String message) {
		super(message);
	}

	public SerializationException (Throwable cause) {
		super("", cause);
	}

	public String getMessage () {
		if (trace == null) return super.getMessage();
		StringBuffer buffer = new StringBuffer(512);
		buffer.append(super.getMessage());
		if (buffer.length() > 0) buffer.append('\n');
		buffer.append("Serialization trace:");
		buffer.append(trace);
		return buffer.toString();
	}

	/** Adds information to the exception message about where in the the object graph serialization failure occurred. Serializers
	 * can catch {@link SerializationException}, add trace information, and rethrow the exception. */
	public void addTrace (String info) {
		if (info == null) throw new IllegalArgumentException("info cannot be null.");
		if (trace == null) trace = new StringBuffer(512);
		trace.append('\n');
		trace.append(info);
	}
}
