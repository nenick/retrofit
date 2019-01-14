/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package retrofit2

import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ResponseSuspendCallAdapter<ResponseT, ReturnT>(private val responseType: Type) :
    SuspendCallAdapter<ResponseT, Response<ReturnT>> {

    override fun responseType(): Type {
        return Utils.getParameterUpperBound(0, responseType as ParameterizedType)
    }

    override suspend fun adapt(call: Call<ResponseT>): Response<ReturnT> {
        @Suppress("UNCHECKED_CAST")
        return call.awaitResponse() as Response<ReturnT>
    }
}

suspend fun <T> Call<T>.awaitResponse(): Response<T> {
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            cancel()
        }
        enqueue(object : Callback<T> {
            override fun onResponse(call: Call<T>, response: Response<T>) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call<T>, t: Throwable) {
                continuation.resumeWithException(t)
            }
        })
    }
}