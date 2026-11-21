package com.financetracker.common.security;

public interface CurrentUserProvider {
    /** The id of the user the current request acts on behalf of. */
    long currentUserId();
}
