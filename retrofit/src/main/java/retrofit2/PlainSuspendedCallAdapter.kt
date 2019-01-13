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

import java.lang.reflect.Type

class PlainSuspendedCallAdapter<ResponseT, ReturnT>(private val responseType: Type) :
    SuspendCallAdapter<ResponseT, ReturnT> {

    override fun responseType(): Type {
        return responseType
    }

    override suspend fun adapt(call: Call<ResponseT>): ReturnT {
        @Suppress("UNCHECKED_CAST")
        return call.await() as ReturnT
    }
}