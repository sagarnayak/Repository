@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package com.sagar.repository

import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sagar.android.logutilmaster.LogUtil
import com.sagar.livedata.SuperMutableLiveData
import com.sagar.modelsandenums.enums.ResultType
import com.sagar.modelsandenums.enums.StatusCode
import com.sagar.modelsandenums.models.Event
import com.sagar.modelsandenums.models.Result
import com.sagar.repository.contracts.SuperRepositoryCallback
import com.sagar.repository.models.NetworkCallTime
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.*

abstract class SuperRepository {
    ////////////////////////////////////////////////////////////////////////////////////////////////
    //fields needs to supplied from outside
    var internalServerErrorMessage = ""
    var fallbackErrorMessage = ""
    var payloadTooLargeMessage = ""
    var notFound = ""
    var timeoutOccurred = "Timeout occurred"
    var networkError = "Network Error"
    var somethingWentWrong = "Something went wrong."

    var TWO_FACTOR_AUTH_ERROR_TYPE = ""
    var ERROR_MESSAGE_DATABASE_ERROR = ""

    lateinit var logUtilForSuper: LogUtil
    ////////////////////////////////////////////////////////////////////////////////////////////////

    var networkCallTimeMaster: NetworkCallTimeMaster = NetworkCallTimeMaster()

    inline fun <reified T> fromJson(json: String): T {
        return Gson().fromJson(json, object : TypeToken<T>() {}.type)
    }

    fun logThisError(error: String) {
        if (this::logUtilForSuper.isInitialized) {
            logUtilForSuper.logE(error)
        }
    }

    //util function
    fun getErrorMessage(throwable: Throwable): String {
        return if (throwable is HttpException) {
            val responseBody = throwable.response()!!.errorBody()
            try {
                val jsonObject = JSONObject(responseBody!!.string())
                jsonObject.getString("error")
            } catch (e: Exception) {
                logThisError(e.message!!)
                e.message!!
            }
        } else (when (throwable) {
            is SocketTimeoutException -> timeoutOccurred
            is IOException -> networkError
            else -> throwable.message
        })!!
    }

    @Suppress("unused")
    private fun getErrorMessage(responseBody: ResponseBody): String {
        return try {
            val jsonObject = JSONObject(responseBody.string())
            jsonObject.getString("error")
        } catch (e: Exception) {
            somethingWentWrong
        }
    }

    lateinit var superRepositoryUnAuthorisedCallbackGlobal: SuperRepositoryCallback<Result>

    fun registerForUnAuthorisedGlobalCallback(callback: SuperRepositoryCallback<Result>) {
        this.superRepositoryUnAuthorisedCallbackGlobal = callback
    }

    inline fun <reified T> makeApiCall(
        observable: Observable<Response<ResponseBody>>,
        responseJsonKeyword: String = "",
        doNotLookForResponseBody: Boolean = false,
        lookForOnlySuccessCode: Boolean = false,
        callback: SuperRepositoryCallback<T>? = null,
        successMutableLiveData: MutableLiveData<Event<T>>? = null,
        errorMutableLiveData: MutableLiveData<Event<Result>>? = null,
        superMutableLiveData: SuperMutableLiveData<T>? = null,
        ignoreUnAuthorisedResponse: Boolean = false
    ) {
        val networkCallTime = NetworkCallTime(startTime = Calendar.getInstance().timeInMillis)

        observable
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                object : Observer<Response<ResponseBody>> {
                    override fun onComplete() {
                        networkCallTime.endTime = Calendar.getInstance().timeInMillis
                        networkCallTimeMaster.gotNetworkCallTime(networkCallTime)
                    }

                    override fun onSubscribe(d: Disposable) {

                    }

                    override fun onNext(t: Response<ResponseBody>) {
                        when (t.code()) {
                            StatusCode.OK.code -> {
                                process200SeriesResponse(
                                    responseJsonKeyword,
                                    doNotLookForResponseBody,
                                    lookForOnlySuccessCode,
                                    callback,
                                    successMutableLiveData,
                                    errorMutableLiveData,
                                    t,
                                    superMutableLiveData
                                )
                            }
                            StatusCode.Created.code -> {
                                process200SeriesResponse(
                                    responseJsonKeyword,
                                    doNotLookForResponseBody,
                                    lookForOnlySuccessCode,
                                    callback,
                                    successMutableLiveData,
                                    errorMutableLiveData,
                                    t,
                                    superMutableLiveData
                                )
                            }
                            StatusCode.Unauthorized.code -> {
                                processUnAuthorisedResponse(
                                    t,
                                    ignoreUnAuthorisedResponse,
                                    errorMutableLiveData,
                                    superMutableLiveData,
                                    callback
                                )
                            }
                            StatusCode.PayloadTooLarge.code -> {
                                processPayloadTooLargeResponse(
                                    errorMutableLiveData,
                                    superMutableLiveData,
                                    callback
                                )
                            }
                            StatusCode.NotFound.code -> {
                                processNotFoundResponse(
                                    errorMutableLiveData,
                                    superMutableLiveData,
                                    callback
                                )
                            }
                            StatusCode.InternalServerError.code -> {
                                processInternalServerErrorResponse(
                                    errorMutableLiveData,
                                    superMutableLiveData,
                                    callback
                                )
                            }
                            StatusCode.Forbidden.code -> {
                                processForbiddenResponse(
                                    errorMutableLiveData,
                                    superMutableLiveData,
                                    callback
                                )
                            }
                            else -> {
                                try {
                                    val errorBody = t.errorBody()
                                    val errorResponse: Result =
                                        fromJson(errorBody!!.string())
                                    errorMutableLiveData?.postValue(
                                        Event(
                                            errorResponse
                                        )
                                    )
                                    superMutableLiveData?.getFail()?.postValue(
                                        Event(
                                            errorResponse
                                        )
                                    )
                                    callback?.error(errorResponse)
                                } catch (ex: java.lang.Exception) {
                                    logThisError(ex.toString())
                                    ex.printStackTrace()
                                    val errorReply = Result(
                                        StatusCode.FailedToParseData.code,
                                        message = "We are having some error. Please try after some time.",
                                        result = ResultType.FAIL
                                    )
                                    errorMutableLiveData?.postValue(
                                        Event(
                                            errorReply
                                        )
                                    )
                                    superMutableLiveData?.getFail()?.postValue(
                                        Event(
                                            errorReply
                                        )
                                    )
                                    callback?.error(errorReply)
                                }
                            }
                        }
                    }

                    override fun onError(e: Throwable) {
                        val errorMessage = getErrorMessage(e)
                        val errorReply = Result(
                            StatusCode.FailedToParseData.code,
                            errorMessage,
                            errorMessage,
                            result = ResultType.FAIL
                        )
                        if (
                            errorReply.type.equals("Network Error", true)
                        ) {
                            errorReply.message = fallbackErrorMessage
                        }
                        errorMutableLiveData?.postValue(
                            Event(
                                errorReply
                            )
                        )
                        superMutableLiveData?.getFail()?.postValue(
                            Event(
                                errorReply
                            )
                        )

                        callback?.error(errorReply)
                    }
                }
            )
    }

    inline fun <reified T> processForbiddenResponse(
        errorMutableLiveData: MutableLiveData<Event<Result>>?,
        superMutableLiveData: SuperMutableLiveData<T>?,
        callback: SuperRepositoryCallback<T>?
    ) {
        val errorResponse =
            Result(
                message = internalServerErrorMessage,
                code = StatusCode.InternalServerError.code,
                result = ResultType.FAIL
            )
        errorMutableLiveData?.postValue(
            Event(
                errorResponse
            )
        )
        superMutableLiveData?.getFail()?.postValue(
            Event(
                errorResponse
            )
        )
        callback?.noContent()
    }

    inline fun <reified T> processInternalServerErrorResponse(
        errorMutableLiveData: MutableLiveData<Event<Result>>?,
        superMutableLiveData: SuperMutableLiveData<T>?,
        callback: SuperRepositoryCallback<T>?
    ) {
        val errorResponse =
            Result(
                message = internalServerErrorMessage,
                code = StatusCode.InternalServerError.code,
                result = ResultType.FAIL
            )
        errorMutableLiveData?.postValue(
            Event(
                errorResponse
            )
        )
        superMutableLiveData?.getFail()?.postValue(
            Event(
                errorResponse
            )
        )
        callback?.noContent()
    }

    inline fun <reified T> processNotFoundResponse(
        errorMutableLiveData: MutableLiveData<Event<Result>>?,
        superMutableLiveData: SuperMutableLiveData<T>?,
        callback: SuperRepositoryCallback<T>?
    ) {
        val errorResponse =
            Result(
                message = notFound,
                code = StatusCode.NotFound.code,
                result = ResultType.FAIL
            )
        errorMutableLiveData?.postValue(
            Event(
                errorResponse
            )
        )
        superMutableLiveData?.getFail()?.postValue(
            Event(
                errorResponse
            )
        )
        callback?.noContent()
    }

    inline fun <reified T> processPayloadTooLargeResponse(
        errorMutableLiveData: MutableLiveData<Event<Result>>?,
        superMutableLiveData: SuperMutableLiveData<T>?,
        callback: SuperRepositoryCallback<T>?
    ) {
        val errorResponse =
            Result(
                message = payloadTooLargeMessage,
                code = StatusCode.PayloadTooLarge.code,
                result = ResultType.FAIL
            )
        errorMutableLiveData?.postValue(
            Event(
                errorResponse
            )
        )
        superMutableLiveData?.getFail()?.postValue(
            Event(
                errorResponse
            )
        )
        callback?.error(errorResponse)
    }

    inline fun <reified T> processUnAuthorisedResponse(
        t: Response<ResponseBody>,
        ignoreUnAuthorisedResponse: Boolean,
        errorMutableLiveData: MutableLiveData<Event<Result>>?,
        superMutableLiveData: SuperMutableLiveData<T>?,
        callback: SuperRepositoryCallback<T>?
    ) {
        try {
            val errorBody = t.errorBody()
            val errorResponse: Result =
                fromJson(errorBody!!.string())
            if (!ignoreUnAuthorisedResponse) {
                if (errorResponse.type == TWO_FACTOR_AUTH_ERROR_TYPE) {
                    superRepositoryUnAuthorisedCallbackGlobal.let {
                        superRepositoryUnAuthorisedCallbackGlobal.twoFactorNotDone()
                    }
                } else {
                    superRepositoryUnAuthorisedCallbackGlobal.let {
                        superRepositoryUnAuthorisedCallbackGlobal.notAuthorised()
                    }
                }
            }
            errorMutableLiveData?.postValue(
                Event(
                    errorResponse
                )
            )
            superMutableLiveData?.getFail()?.postValue(
                Event(
                    errorResponse
                )
            )
            callback?.error(errorResponse)
        } catch (ex: java.lang.Exception) {
            ex.printStackTrace()
            logThisError(ex.toString())
            val errorReply = Result(
                StatusCode.FailedToParseData.code,
                message = "We are having some error. Please try after some time.",
                result = ResultType.FAIL
            )
            errorMutableLiveData?.postValue(
                Event(
                    errorReply
                )
            )
            superMutableLiveData?.getFail()?.postValue(
                Event(
                    errorReply
                )
            )
            callback?.error(errorReply)
        }
    }

    inline fun <reified T> process200SeriesResponse(
        responseJsonKeyword: String = "",
        doNotLookForResponseBody: Boolean = false,
        lookForOnlySuccessCode: Boolean = false,
        callback: SuperRepositoryCallback<T>? = null,
        successMutableLiveData: MutableLiveData<Event<T>>? = null,
        errorMutableLiveData: MutableLiveData<Event<Result>>? = null,
        response: Response<ResponseBody>,
        superMutableLiveData: SuperMutableLiveData<T>? = null
    ) {
        if (lookForOnlySuccessCode) {
            successMutableLiveData?.postValue(
                Event(
                    T::class.java.newInstance()
                )
            )

            callback?.success(
                T::class.java.newInstance()
            )

            return
        }
        try {
            val toParse = response.body()
            val jsonObject = JSONObject(toParse!!.string())

            var statusReply = Result(
                StatusCode.OK.code,
                "Success",
                result = ResultType.OK
            )

            val doesItHasStatusObject = jsonObject.has("code")

            if (
                doesItHasStatusObject
            ) {
                val code = jsonObject.getInt("code")
                val message = jsonObject.getString("message")

                statusReply =
                    Result(
                        code = code,
                        message = message,
                        result = ResultType.OK
                    )
            }

            if (statusReply.isResultOk()) {
                if (doNotLookForResponseBody) {
                    successMutableLiveData?.postValue(
                        Event(
                            T::class.java.newInstance()
                        )
                    )
                    superMutableLiveData?.getSuccess()?.postValue(
                        Event(
                            T::class.java.newInstance()
                        )
                    )

                    callback?.success(
                        T::class.java.newInstance()
                    )

                    return
                }

                val resultToSendString = if (responseJsonKeyword != "") {
                    jsonObject.getString(responseJsonKeyword)
                } else {
                    jsonObject.toString()
                }

                val resultToSend =
                    if (
                        !resultToSendString.contains("{") &&
                        !resultToSendString.contains("[")
                    )
                        resultToSendString as T
                    else if (
                        T::class.java.newInstance() is Collections
                    ) {
                        resultToSendString as T
                    } else {
                        fromJson(
                            resultToSendString
                        )
                    }

                val eventToReturn = Event(
                    resultToSend
                )

                successMutableLiveData?.postValue(
                    eventToReturn
                )
                superMutableLiveData?.getSuccess()?.postValue(
                    eventToReturn
                )

                callback?.success(
                    resultToSend
                )
            } else {
                errorMutableLiveData?.postValue(
                    Event(
                        statusReply
                    )
                )
                superMutableLiveData?.getFail()?.postValue(
                    Event(
                        statusReply
                    )
                )

                callback?.error(statusReply)
            }
        } catch (ex: Exception) {
            logThisError(ex.toString())
            val errorReply = Result(
                StatusCode.FailedToParseData.code,
                "Failed to parse data",
                result = ResultType.FAIL
            )
            errorMutableLiveData?.postValue(
                Event(
                    errorReply
                )
            )
            superMutableLiveData?.getFail()?.postValue(
                Event(
                    errorReply
                )
            )

            callback?.error(errorReply)
        }
    }

    inline fun <reified T> makeDatabaseCall(
        observable: Observable<T>? = null,
        completable: Completable? = null,
        readDataOnlyOnce: Boolean = true,
        canReadResultOnlyOnce: Boolean = true,
        callback: SuperRepositoryCallback<T>? = null,
        successMutableLiveData: MutableLiveData<Event<T>>? = null,
        errorMutableLiveData: MutableLiveData<Event<Result>>? = null,
        superMutableLiveData: SuperMutableLiveData<T>? = null
    ) {
        observable?.let { observablePositive ->
            val observableToWatch =
                if (readDataOnlyOnce) observablePositive.take(1) else observablePositive

            observableToWatch
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        successMutableLiveData?.postValue(
                            Event(
                                it,
                                canReadResultOnlyOnce
                            )
                        )
                        superMutableLiveData?.getSuccess()?.postValue(
                            Event(
                                it,
                                canReadResultOnlyOnce
                            )
                        )
                        callback?.success(it)
                    },
                    {
                        val resultToSend = Result(
                            StatusCode.Unknown.code,
                            it.message ?: ERROR_MESSAGE_DATABASE_ERROR,
                            result = ResultType.FAIL
                        )
                        errorMutableLiveData?.postValue(
                            Event(
                                resultToSend
                            )
                        )
                        superMutableLiveData?.getFail()?.postValue(
                            Event(
                                resultToSend
                            )
                        )
                        callback?.error(resultToSend)
                    }
                )
        }

        completable?.let { completablePositive ->
            completablePositive
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        successMutableLiveData?.postValue(
                            Event(
                                T::class.java.newInstance(),
                                canReadResultOnlyOnce
                            )
                        )
                        superMutableLiveData?.getSuccess()?.postValue(
                            Event(
                                T::class.java.newInstance(),
                                canReadResultOnlyOnce
                            )
                        )
                        callback?.success(T::class.java.newInstance())
                    },
                    {
                        val resultToSend = Result(
                            StatusCode.Unknown.code,
                            it.message ?: ERROR_MESSAGE_DATABASE_ERROR,
                            result = ResultType.FAIL
                        )
                        errorMutableLiveData?.postValue(
                            Event(
                                resultToSend
                            )
                        )
                        superMutableLiveData?.getFail()?.postValue(
                            Event(
                                resultToSend
                            )
                        )
                        callback?.error(resultToSend)
                    }
                )
        }
    }
}