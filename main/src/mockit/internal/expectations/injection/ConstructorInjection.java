/*
 * Copyright (c) 2006-2014 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit.internal.expectations.injection;

import java.lang.reflect.*;
import java.util.*;

import mockit.internal.expectations.mocking.*;
import mockit.internal.util.*;
import static mockit.internal.expectations.injection.InjectionPoint.*;
import static mockit.internal.util.ConstructorReflection.*;
import static mockit.internal.util.Utilities.*;

import org.jetbrains.annotations.*;

final class ConstructorInjection
{
   @NotNull private final InjectionState injectionState;
   @NotNull private final Constructor<?> constructor;

   ConstructorInjection(@NotNull InjectionState injectionState, @NotNull Constructor<?> constructor)
   {
      this.injectionState = injectionState;
      this.constructor = constructor;
   }

   @NotNull
   Object instantiate(@NotNull List<MockedType> injectablesForConstructor)
   {
      Type[] parameterTypes = constructor.getGenericParameterTypes();
      int n = parameterTypes.length;
      Object[] arguments = n == 0 ? NO_ARGS : new Object[n];
      boolean varArgs = constructor.isVarArgs();

      if (varArgs) {
         n--;
      }

      for (int i = 0; i < n; i++) {
         MockedType injectable = injectablesForConstructor.get(i);
         Object value = getArgumentValueToInject(injectable);
         arguments[i] = wrapInProviderIfNeeded(parameterTypes[i], value);
      }

      if (varArgs) {
         arguments[n] = obtainInjectedVarargsArray(parameterTypes, n);
      }

      return invoke(constructor, arguments);
   }

   @NotNull
   private Object obtainInjectedVarargsArray(@NotNull Type[] parameterTypes, int varargsParameterIndex)
   {
      Type varargsElementType = getTypeOfInjectionPointFromVarargsParameter(parameterTypes, varargsParameterIndex);
      injectionState.setTypeOfInjectionPoint(varargsElementType);

      List<Object> varargValues = new ArrayList<Object>();
      MockedType injectable;

      while ((injectable = injectionState.findNextInjectableForInjectionPoint()) != null) {
         Object value = injectionState.getValueToInject(injectable);

         if (value != null) {
            value = wrapInProviderIfNeeded(varargsElementType, value);
            varargValues.add(value);
         }
      }

      int elementCount = varargValues.size();
      Object varargArray = Array.newInstance(getClassType(varargsElementType), elementCount);

      for (int i = 0; i < elementCount; i++) {
         Array.set(varargArray, i, varargValues.get(i));
      }

      return varargArray;
   }

   @NotNull
   private Object getArgumentValueToInject(@NotNull MockedType injectable)
   {
      Object argument = injectionState.getValueToInject(injectable);

      if (argument == null) {
         throw new IllegalArgumentException(
            "No injectable value available" + missingInjectableDescription(injectable.mockId));
      }

      return argument;
   }

   @NotNull
   private String missingInjectableDescription(@NotNull String name)
   {
      String classDesc = mockit.external.asm.Type.getInternalName(constructor.getDeclaringClass());
      String constructorDesc = "<init>" + mockit.external.asm.Type.getConstructorDescriptor(constructor);
      String constructorDescription = new MethodFormatter(classDesc, constructorDesc).toString();

      return " for parameter \"" + name + "\" in constructor " + constructorDescription.replace("java.lang.", "");
   }
}
