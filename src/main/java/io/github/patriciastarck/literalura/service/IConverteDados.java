package io.github.patriciastarck.literalura.service;

public interface IConverteDados {
    <T> T obterDados(String json, Class<T> classe);
}
