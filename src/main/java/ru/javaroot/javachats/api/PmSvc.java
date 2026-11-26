package ru.javaroot.javachats.api;

import java.util.concurrent.CompletionStage;

/**
 * Сервис доставки личных сообщений с учётом региональных потоков Folia.
 */
public interface PmSvc {
    CompletionStage<PmRes> send(PmReq request);
}
