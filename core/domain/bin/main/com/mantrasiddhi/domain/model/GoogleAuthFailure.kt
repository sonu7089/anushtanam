package com.mantrasiddhi.domain.model

enum class GoogleAuthProblem { OFFLINE, COLLISION, EXPIRED, UNAVAILABLE, WRONG_ACCOUNT, ROLE_MISMATCH, UNKNOWN }

class GoogleAuthFailure(val problem: GoogleAuthProblem, cause: Throwable? = null) :
    Exception(problem.name, cause)
