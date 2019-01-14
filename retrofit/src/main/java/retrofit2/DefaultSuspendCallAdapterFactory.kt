package retrofit2

import retrofit2.SuspendCallAdapter.Factory
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class DefaultSuspendCallAdapterFactory : SuspendCallAdapter.Factory() {
    override fun get(returnType: Type, annotations: Array<Annotation>, retrofit: Retrofit): SuspendCallAdapter<*, *>? {
        if(Factory.getRawType(returnType) == Response::class.java && returnType is ParameterizedType) {
            return ResponseSuspendCallAdapter<Any, Any>(returnType)
        }

        return PlainSuspendedCallAdapter<Any, Any>(returnType)
    }
}