package com.ordering.common.feign;

import com.ordering.common.exception.*;
import feign.Response;
import feign.codec.ErrorDecoder;

public class FeignErrorDecoder implements ErrorDecoder {

    @Override
    public Exception decode(String methodKey, Response response) {
        return switch (response.status()) {
            case 400 -> new BadRequestException("Bad request to downstream service");
            case 401 -> new UnauthorizedException("Unauthorized to downstream service");
            case 403 -> new ForbiddenException("Forbidden by downstream service");
            case 404 -> new ResourceNotFoundException("Resource not found in downstream service");
            default -> new DownstreamServiceException("Downstream service error");
        };
    }
}
