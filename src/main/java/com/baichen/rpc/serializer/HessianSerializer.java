package com.baichen.rpc.serializer;

import com.caucho.hessian.io.HessianInput;
import com.caucho.hessian.io.HessianOutput;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@Slf4j
public class HessianSerializer implements Serializer {
    @Override
    public byte[] serialize(Object obj) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            HessianOutput hessianOutput = new HessianOutput(baos);
            hessianOutput.writeObject(obj);
            hessianOutput.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("HessianSerializer serialize error", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes, Class<T> clazz) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            HessianInput hessianInput = new HessianInput(bais);
            return (T) hessianInput.readObject();
        } catch (Exception e) {
            log.error("HessianSerializer deserialize error", e);
            return null;
        }
    }
}
