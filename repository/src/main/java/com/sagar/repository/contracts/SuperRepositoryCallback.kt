package com.sagar.repository.contracts

import com.sagar.modelsandenums.models.Result

interface SuperRepositoryCallback<in T> {
    fun success(result: T) {}
    fun notAuthorised() {}
    fun twoFactorNotDone() {}
    fun noContent() {}
    fun error(result: Result) {}
}