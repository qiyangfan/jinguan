package com.longzhong.jinguan.common;

/** key 已终态吊销(重绑/复用被拒);不可逆语义,映射 409。 */
public class KeyRevokedException extends DomainException {

  public KeyRevokedException(String keyId) {
    super("key 已吊销,不可重新绑定: " + keyId);
  }
}
