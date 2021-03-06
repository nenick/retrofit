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
package retrofit2;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import javax.annotation.Nullable;
import kotlin.coroutines.Continuation;
import okhttp3.Call;
import okhttp3.ResponseBody;

import static retrofit2.Utils.getRawType;
import static retrofit2.Utils.methodError;

/** Adapts an invocation of an interface method into an HTTP call. */
final class HttpServiceMethod<ResponseT, ReturnT> extends ServiceMethod<ReturnT> {
  /**
   * Inspects the annotations on an interface method to construct a reusable service method that
   * speaks HTTP. This requires potentially-expensive reflection so it is best to build each service
   * method only once and reuse it.
   */
  static <ResponseT, ReturnT> HttpServiceMethod<ResponseT, ReturnT> parseAnnotations(
      Retrofit retrofit, Method method, RequestFactory requestFactory) {
    CallAdapter<ResponseT, ReturnT> callAdapter = null;
    SuspendCallAdapter<ResponseT, ReturnT> suspendCallAdapter = null;
    Type responseType;
    if (requestFactory.isKotlinSuspendFunction) {
      suspendCallAdapter = createSuspendCallAdapter(retrofit, method);
      responseType = suspendCallAdapter.responseType();
    } else {
      callAdapter = createCallAdapter(retrofit, method);
      responseType = callAdapter.responseType();
    }

    if (responseType == okhttp3.Response.class) {
      throw methodError(method, "'"
          + getRawType(responseType).getName()
          + "' is not a valid response body type. Did you mean ResponseBody?");
    }
    if (responseType == Response.class) {
      throw methodError(method, "Response must include generic type (e.g., Response<String>)");
    }
    // TODO support Unit for Kotlin?
    if (requestFactory.httpMethod.equals("HEAD") && !Void.class.equals(responseType)) {
      throw methodError(method, "HEAD method must use Void as response type.");
    }

    Converter<ResponseBody, ResponseT> responseConverter =
        createResponseConverter(retrofit, method, responseType);

    okhttp3.Call.Factory callFactory = retrofit.callFactory;
    return new HttpServiceMethod<>(requestFactory, callFactory, callAdapter, suspendCallAdapter, responseConverter);
  }

  private static <ResponseT, ReturnT> SuspendCallAdapter<ResponseT, ReturnT> createSuspendCallAdapter(
          Retrofit retrofit, Method method) {
    Type[] parameterTypes = method.getGenericParameterTypes();
    Type continuationType = parameterTypes[parameterTypes.length - 1];
    Type responseType = Utils.getParameterLowerBound(0, (ParameterizedType) continuationType);
    //noinspection unchecked
    return (SuspendCallAdapter<ResponseT, ReturnT>) retrofit.suspendCallAdapter(responseType, method.getAnnotations());
  }

  private static <ResponseT, ReturnT> CallAdapter<ResponseT, ReturnT> createCallAdapter(
      Retrofit retrofit, Method method) {
    Type returnType = method.getGenericReturnType();
    Annotation[] annotations = method.getAnnotations();
    try {
      //noinspection unchecked
      return (CallAdapter<ResponseT, ReturnT>) retrofit.callAdapter(returnType, annotations);
    } catch (RuntimeException e) { // Wide exception range because factories are user code.
      throw methodError(method, e, "Unable to create call adapter for %s", returnType);
    }
  }

  private static <ResponseT> Converter<ResponseBody, ResponseT> createResponseConverter(
      Retrofit retrofit, Method method, Type responseType) {
    Annotation[] annotations = method.getAnnotations();
    try {
      return retrofit.responseBodyConverter(responseType, annotations);
    } catch (RuntimeException e) { // Wide exception range because factories are user code.
      throw methodError(method, e, "Unable to create converter for %s", responseType);
    }
  }

  private final RequestFactory requestFactory;
  private final okhttp3.Call.Factory callFactory;
  /** Null indicates a Kotlin coroutine service method. */
  private final @Nullable CallAdapter<ResponseT, ReturnT> callAdapter;
  private final @Nullable SuspendCallAdapter<ResponseT, ReturnT> suspendCallAdapter;
  private final Converter<ResponseBody, ResponseT> responseConverter;

  private HttpServiceMethod(RequestFactory requestFactory, Call.Factory callFactory,
      @Nullable CallAdapter<ResponseT, ReturnT> callAdapter, @Nullable SuspendCallAdapter<ResponseT, ReturnT> suspendCallAdapter,
      Converter<ResponseBody, ResponseT> responseConverter) {
    this.requestFactory = requestFactory;
    this.callFactory = callFactory;
    this.callAdapter = callAdapter;
    this.suspendCallAdapter = suspendCallAdapter;
    this.responseConverter = responseConverter;
  }

  @Override ReturnT invoke(Object[] args) {
    OkHttpCall<ResponseT> call =
        new OkHttpCall<>(requestFactory, args, callFactory, responseConverter);

    if (callAdapter != null) {
      return callAdapter.adapt(call);
    }

    Object continuation = args[args.length - 1];
    //noinspection ConstantConditions,unchecked Guaranteed by parseAnnotations above.
    return (ReturnT) suspendCallAdapter.adapt(call, (Continuation<ReturnT>) continuation);
  }
}
