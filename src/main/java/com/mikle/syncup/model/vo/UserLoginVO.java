package com.mikle.syncup.model.vo;

import com.mikle.syncup.model.domain.User;
import lombok.Data;

import java.io.Serializable;

/**
 * User login response.
 */
@Data
public class UserLoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Safety user info.
     */
    private User user;

    /**
     * Raw token value. The frontend should send it with the configured token prefix.
     */
    private String token;

    /**
     * Request header name used by Sa-Token.
     */
    private String tokenName;

    /**
     * Request token prefix.
     */
    private String tokenPrefix;
}
