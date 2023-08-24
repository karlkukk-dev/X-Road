/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.signer.protocol;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.signer.TemporaryHelper;
import ee.ria.xroad.signer.protocol.dto.CodedExceptionProto;
import ee.ria.xroad.signer.tokenmanager.token.AbstractTokenWorker;

import com.google.protobuf.AbstractMessage;
import io.grpc.Status;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import static com.google.protobuf.Any.pack;
import static java.util.Optional.ofNullable;

/**
 * @param <R>
 * @param <T>
 */
@Slf4j
public abstract class AbstractRpcHandler<R extends AbstractMessage, T extends AbstractMessage> {
    @Autowired
    protected TemporaryAkkaMessenger temporaryAkkaMessenger;

    protected abstract T handle(R request) throws Exception;

    public void processSingle(R request, StreamObserver<T> responseObserver) {
        try {
            var response = handle(request);

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleException(e, responseObserver);
        }
    }

    protected AbstractTokenWorker getTokenWorker(String tokenId) {
        return TemporaryHelper.getTokenWorker(tokenId);
    }

    private void handleException(Exception exception, StreamObserver<T> responseObserver) {
        if (exception instanceof CodedException) {
            CodedException codedException = (CodedException) exception;

            com.google.rpc.Status status = com.google.rpc.Status.newBuilder()
                    .setCode(Status.Code.INTERNAL.value())
                    .setMessage(codedException.getMessage())
                    .addDetails(pack(toProto(codedException)))
                    .build();

            responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        } else {
            log.warn("Unhandled exception was thrown by gRPC handler.", exception);
            responseObserver.onError(exception);
        }
    }

    private CodedExceptionProto toProto(CodedException codedException) {
        final CodedExceptionProto.Builder codedExceptionBuilder = CodedExceptionProto.newBuilder();

        ofNullable(codedException.getFaultCode()).ifPresent(codedExceptionBuilder::setFaultCode);
        ofNullable(codedException.getFaultActor()).ifPresent(codedExceptionBuilder::setFaultActor);
        ofNullable(codedException.getFaultDetail()).ifPresent(codedExceptionBuilder::setFaultDetail);
        ofNullable(codedException.getFaultString()).ifPresent(codedExceptionBuilder::setFaultString);
        ofNullable(codedException.getTranslationCode()).ifPresent(codedExceptionBuilder::setTranslationCode);

        return codedExceptionBuilder.build();
    }
}
