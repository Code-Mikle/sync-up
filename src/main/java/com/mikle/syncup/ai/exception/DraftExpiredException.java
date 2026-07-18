package com.mikle.syncup.ai.exception;

import com.mikle.syncup.common.ErrorCode;
import com.mikle.syncup.exception.BusinessException;

/**
 * Marks a draft as expired while allowing that status change to commit.
 */
public class DraftExpiredException extends BusinessException {

    public DraftExpiredException() {
        super(ErrorCode.PARAMS_ERROR, "draft has expired");
    }
}
