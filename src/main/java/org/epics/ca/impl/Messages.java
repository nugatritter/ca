package org.epics.ca.impl;

import java.nio.ByteBuffer;

import org.epics.ca.Constants;

public final class Messages {
	
	/**
	 * Calculate aligned message size.
	 * @param align				alignment to be used.
	 * @param nonAlignedSize	current non-aligned size.
	 * @return aligned size.
	 */
	public static int calculateAlignedSize(int align, int nonAlignedSize)
	{
		return ((nonAlignedSize+align-1)/align)*align;
	}

	/**
	 * Start CA message.
	 * @param transport	transport to be used when sending.
	 * @return	filled buffer, if given buffer size is less that header size,
	 * 			then new buffer is allocated and returned.
	 */
	public static ByteBuffer startCAMessage(Transport transport,
											short command, int payloadSize,
											short dataType, int dataCount,
											int parameter1, int parameter2)
	{ 
		boolean useExtendedHeader = payloadSize >= 0xFFFF || dataCount >= 0xFFFF;
		
		// check if supported by current transport protocol revision
		if (useExtendedHeader && transport != null && transport.getMinorRevision() < 9) 
			throw new IllegalArgumentException("Out of bounds.");

		int requiredSize = useExtendedHeader ? 
								Constants.CA_EXTENDED_MESSAGE_HEADER_SIZE :
								Constants.CA_MESSAGE_HEADER_SIZE;
			
		ByteBuffer buffer = transport.acquireSendBuffer(requiredSize);
			
		// standard header
		if (!useExtendedHeader)
		{
			buffer.putShort(command);
			// conversion int -> unsigned short is done right
			buffer.putShort((short)payloadSize);
			buffer.putShort(dataType);
			// conversion int -> unsigned short is done right
			buffer.putShort((short)dataCount);
			buffer.putInt(parameter1);
			buffer.putInt(parameter2);
		}
		// extended header 
		else
		{
			buffer.putShort(command);
			buffer.putShort((short)0xFFFF);
			buffer.putShort(dataType);
			buffer.putShort((short)0x0000);
			buffer.putInt(parameter1);
			buffer.putInt(parameter2);
			buffer.putInt(payloadSize);
			buffer.putInt(dataCount);
		}

		return buffer;
	}
	
	/**
	 * Generate search request message.
	 * A special case implementation since message is sent via UDP.
	 * @param transport
	 * @param requestMessage
	 * @param name
	 * @param cid
	 */
	public static final boolean generateSearchRequestMessage(Transport transport, ByteBuffer buffer,
			String name, int cid)
	{
		// name length was already validated at channel creation time

		int unalignedMessageSize = Constants.CA_MESSAGE_HEADER_SIZE + name.length() + 1;
		int alignedMessageSize = calculateAlignedSize(8, unalignedMessageSize);
		if (buffer.remaining() < alignedMessageSize)
			return false;
		
		buffer.putShort((short)6);
		// conversion int -> unsigned short is done right
		buffer.putShort((short)(alignedMessageSize - Constants.CA_MESSAGE_HEADER_SIZE));
		buffer.putShort(Constants.CA_SEARCH_DONTREPLY);
		// conversion int -> unsigned short is done right
		buffer.putShort((short)transport.getMinorRevision());
		buffer.putInt(cid);
		buffer.putInt(cid);

		// append zero-terminated string and align message
		buffer.put(name.getBytes());
		// terminate with 0 and pad
        for (int i = alignedMessageSize - unalignedMessageSize + 1; i > 0; i--)
            buffer.put((byte)0);
		
		return true;
	}
	
	/**
	 * Generate version request message.
	 * @param transport
	 * @param buffer
	 * @param priority
	 * @param sequenceNumber
	 * @param isSequenceNumberValid
	 * @return generated version message buffer.
	 */
	public static final void generateVersionRequestMessage(
			Transport transport, ByteBuffer buffer, short priority, 
			int sequenceNumber, boolean isSequenceNumberValid)
	{
		short isSequenceNumberValidCode = isSequenceNumberValid ? (short)1 : (short)0;
		
		buffer.putShort((short)0);
		// conversion int -> unsigned short is done right
		buffer.putShort((short)0);
		buffer.putShort(isSequenceNumberValid ? isSequenceNumberValidCode : priority);
		// conversion int -> unsigned short is done right
		buffer.putShort((short)transport.getMinorRevision());
		buffer.putInt(sequenceNumber);
		buffer.putInt(0);
	}
	
	/**
	 * Version message.
	 * @param transport
	 * @param priority
	 * @param sequenceNumber
	 * @param isSequenceNumberValid
	 */
	public static void versionMessage(
			Transport transport, short priority, 
			int sequenceNumber, boolean isSequenceNumberValid)
	{
		
		boolean ignore = true;
		try
		{
			short isSequenceNumberValidCode = isSequenceNumberValid ? (short)1 : (short)0;
			
			startCAMessage(transport,
					(short)0,
					0,
					isSequenceNumberValid ? isSequenceNumberValidCode : priority,
					(short)transport.getMinorRevision(),
					sequenceNumber,
					0);
			ignore = false;
		}
		finally
		{
			transport.releaseSendBuffer(ignore, false);
		}
	}
	
	/**
	 * Hostname message.
	 * @param transport
	 * @param hostName
	 */
	public static void hostNameMessage(
			Transport transport, String hostName)
	{
		// compatibility check
		if (transport.getMinorRevision() < 1)
			return;

		int unalignedMessageSize = Constants.CA_MESSAGE_HEADER_SIZE + hostName.length() + 1;
		int alignedMessageSize = calculateAlignedSize(8, unalignedMessageSize);
	    
		boolean ignore = true;
		try
		{
			ByteBuffer buffer = startCAMessage(transport,
					(short)21,
					alignedMessageSize - Constants.CA_MESSAGE_HEADER_SIZE,
					(short)0,
					0,
					0,
					0);
			
			// append zero-terminated string and align message
			buffer.put(hostName.getBytes());
			// terminate with 0 and pad
	        for (int i = alignedMessageSize - unalignedMessageSize + 1; i > 0; i--)
	            buffer.put((byte)0);
			
			ignore = false;
		}
		finally
		{
			transport.releaseSendBuffer(ignore, false);
		}
	}

	/**
	 * Username message.
	 * @param transport
	 * @param userName
	 */
	public static void userNameMessage(
			Transport transport, String userName)
	{
		// compatibility check
		if (transport.getMinorRevision() < 1)
			return;

		int unalignedMessageSize = Constants.CA_MESSAGE_HEADER_SIZE + userName.length() + 1;
		int alignedMessageSize = calculateAlignedSize(8, unalignedMessageSize);
	    
		boolean ignore = true;
		try
		{
			ByteBuffer buffer = startCAMessage(transport,
					(short)20,
					alignedMessageSize - Constants.CA_MESSAGE_HEADER_SIZE,
					(short)0,
					0,
					0,
					0);
			
			// append zero-terminated string and align message
			buffer.put(userName.getBytes());
			// terminate with 0 and pad
	        for (int i = alignedMessageSize - unalignedMessageSize + 1; i > 0; i--)
	            buffer.put((byte)0);
			
			ignore = false;
		}
		finally
		{
			transport.releaseSendBuffer(ignore, false);
		}
	}
	
	/**
	 * Create channel message.
	 * @param transport
	 * @param channelName
	 * @param cid
	 */
	public static void createChannelMessage(
			Transport transport, String channelName, int cid)
	{
		// v4.4+ or newer
		if (transport.getMinorRevision() < 4)
		{
			// no name used, since cid as already a sid
			channelName = null;
		}
		 
		int binaryNameLength = 0;
		if (channelName != null)
			binaryNameLength = channelName.length() + 1;

		int unalignedMessageSize = Constants.CA_MESSAGE_HEADER_SIZE + binaryNameLength;
		int alignedMessageSize = calculateAlignedSize(8, unalignedMessageSize);
	    
		boolean ignore = true;
		try
		{
			ByteBuffer buffer = startCAMessage(transport,
					(short)18,
					alignedMessageSize - Constants.CA_MESSAGE_HEADER_SIZE,
					(short)0,
					0,
					cid,
					transport.getMinorRevision());
			
			if (binaryNameLength > 0)
			{
				// append zero-terminated string and align message
				buffer.put(channelName.getBytes());
				// terminate with 0 and pad
		        for (int i = alignedMessageSize - unalignedMessageSize + 1; i > 0; i--)
		            buffer.put((byte)0);
			}
			
			ignore = false;
		}
		finally
		{
			transport.releaseSendBuffer(ignore, false);
		}
	}
}