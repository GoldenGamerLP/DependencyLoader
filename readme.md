[![CodeFactor](https://www.codefactor.io/repository/github/goldengamerlp/dependencyloader/badge)](https://www.codefactor.io/repository/github/goldengamerlp/dependencyloader) [![](https://jitpack.io/v/GoldenGamerLP/DependencyLoader.svg)](https://jitpack.io/#GoldenGamerLP/DependencyLoader)

# Simple Automatic Dependency Loader for Java

## What is DPL?

DPL is a simple dependency loader for Java. DPL allows you to load dependencies (Classes) automatically without needing
to
import them by using annotations. It is very useful for large projects and even small projects by making your life
easier and doing the hard work, loading dependencies, injecting dependencies, running methods and ordering dependencies.
I made this project because other injection libraries were too complicated for a simple project, so I started DPL.

## How to use DPL?

DPL is easy 2 use. DPL mainly uses annotations. No need to register classes or running methods by yourself.

### Different Annotations Explained:

- **@AutoLoadable**: This annotation is used to mark a class as a dependency. This class will be loaded automatically.
  No need to register anything.
- **@DependencyConstructor**: This annotation is used to mark a constructor as a dependency constructor. This
  constructor will be used to create an instance of the class. *
  *_The parameters of the constructor are later used for ordering dependencies._**
- **@AutoRun**: This annotation is used to mark any method, without a return value or parameter to be run automatically.
  You can specify in the annotation if the method should be run async and with which priority.
- **@Inject**: This annotation is used to mark a field as being injected. The field will be set to the instance of the
  specified class. _**This class is later used for dependency ordering.**_

## Example

```java
public class Main {

    public static void main(String[] args) {
        DependencyLoader dependencyLoader = DependencyLoader.getInstance();
        
        //Add a standalone dependency which cant be loaded by annotations
        dependencyLoader.addDependency(new MyStandaloneClass());
        
        //Load dependencies and let DPL do the hard work
        dependencyLoader.init();
    }
    
   //Marks this class as a dependency and loads it automatically
    @AutoLoadable
    public static class MyAutoLoadClass {
    
        //Injects the instance of MyStandaloneClass into this field
        @Inject
        private MyStandaloneClass myStandaloneClass;
    
        //Marks this constructor as a dependency constructor. All parameters are used for ordering dependencies. 
        //Make sure that every parameter is either added as a standalone dependency or is able to be loaded by annotations.
        @DependencyConstructor
        public MyAutoLoadClass() {
            System.out.println("MyAutoLoadClass was loaded automatically");
        }
        
        //Run this method automatically and async with priority 1
        @AutoRun(priority = 1, async = true)
        public void run() {
            System.out.println("MyAutoLoadClass was run automatically");
        }
    }
}
```

## Dependency Ordering

DPL uses dependency ordering to make sure that all dependencies are loaded in the correct order.
DPL uses the **parameters** of the dependency constructor and the **injected fields** to order dependencies.

## Remarks and Limitations

- DPL cannot load dependencies without @AutoLoadable and @DependencyConstructor.
- Cyclic dependencies are not supported.
- AutoRun methods are not allowed to have parameters or a return value.

## How to use DPL in your project

### 1. Add Jitpack as a Repository

- Maven:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io/</url> 
    </repository>
</repositories>
```

- Gradle:

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}
```

### 2. Add DependencyLoader and the AnnotationProcessor as a Dependency

- Maven:

```xml
<dependencies>
    <dependency>
        <groupId>com.github.GoldenGamerLP</groupId>
        <artifactId>DependencyLoader</artifactId>
        <version>-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>com.github.GoldenGamerLP</groupId>
        <artifactId>DependencyLoader-AnnotationProcessor</artifactId>
        <version>-SNAPSHOT</version>
        <scope>provided</scope>
        <optional>true</optional>
    </dependency>
</dependencies>
```

- Gradle:

```groovy
dependencies {
    implementation 'com.github.GoldenGamerLP:DependencyLoader:-SNAPSHOT'
    annotationProcessor 'com.github.GoldenGamerLP:DependencyLoader-AnnotationProcessor:-SNAPSHOT'
}
```

## Contributing

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

## License

DPL is licensed under the MIT License. See [MIT License Website](https://opensource.org/license/mit/)
