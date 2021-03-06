package ceid.netcins.exo.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import rice.p2p.commonapi.Endpoint;
import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.p2p.util.rawserialization.JavaDeserializer;
import rice.p2p.util.rawserialization.JavaSerializationException;

/**
 * 
 * @author <a href="mailto:loupasak@ceid.upatras.gr">Andreas Loupasakis</a>
 * @author <a href="mailto:ntarmos@cs.uoi.gr">Nikos Ntarmos</a>
 * @author <a href="mailto:peter@ceid.upatras.gr">Peter Triantafillou</a>
 * 
 * "eXO: Decentralized Autonomous Scalable Social Networking"
 * Proc. 5th Biennial Conf. on Innovative Data Systems Research (CIDR),
 * January 9-12, 2011, Asilomar, California, USA.
 */
public class JavaSerializer {

	public static void serialize(OutputBuffer buf, Object content)
			throws IOException {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);

			// write out object and find its length
			oos.writeObject(content);
			oos.close();

			byte[] temp = baos.toByteArray();
			buf.writeInt(temp.length); // length
			buf.write(temp, 0, temp.length); // content

		} catch (IOException ioe) {
			throw new JavaSerializationException(content, ioe);
		}
	}

	public static Object deserialize(InputBuffer buf, Endpoint endpoint)
			throws IOException {
		byte[] array = new byte[buf.readInt()];
		buf.read(array);

		ObjectInputStream ois = new JavaDeserializer(new ByteArrayInputStream(
				array), endpoint);

		try {
			Object o = ois.readObject();
			return o;
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(
					"Unknown class type in message - cant deserialize.", e);
		}
	}

}