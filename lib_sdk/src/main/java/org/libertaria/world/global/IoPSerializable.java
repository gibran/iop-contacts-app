package org.libertaria.world.global;

/**
 * Created by furszy on 6/30/17.
 */

public interface IoPSerializable<T> {

    T deserialize(byte[] bytes, PlatformSerializer platformSerializer);

    byte[] serialize();

}
