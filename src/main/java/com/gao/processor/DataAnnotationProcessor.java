package com.gao.processor;

import com.gao.annotation.Data;
import com.google.auto.service.AutoService;
import com.squareup.javawriter.JavaWriter;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.*;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

@AutoService(Processor.class)
public class DataAnnotationProcessor extends AbstractProcessor {

    private Messager messager;
    private Elements elements;
    private Filer filer;

    private static final String SEPARATOR = ".";

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.elements = processingEnv.getElementUtils();
        this.filer = processingEnv.getFiler();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> set = new HashSet<>();
        set.add(Data.class.getCanonicalName());
        return Collections.unmodifiableSet(set);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        messager.printMessage(Diagnostic.Kind.NOTE, "开始解析注释并生成源码...");
        boolean isClass = false;
        String classAllName = null;
        //返回被注释的节点
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Data.class);
        Element element = null;
        for (Element e : elements) {
            if (e.getKind() == ElementKind.CLASS && e instanceof TypeElement) {
                TypeElement t = (TypeElement) e;
                isClass = true;
                classAllName = t.getQualifiedName().toString();
                element = t;
                break;
            }
        }
        //未在类上注释则直接返回
        if (!isClass) {
            return true;
        }
        //返回类中的所有节点
        List<? extends Element> enclosedElements = element.getEnclosedElements();
        Map<TypeMirror, Name> fieldMap = new HashMap<>();
        for (Element e : enclosedElements) {
            if (e.getKind() == ElementKind.FIELD) {
                TypeMirror typeMirror = e.asType();
                Name name = e.getSimpleName();
                fieldMap.put(typeMirror, name);
            }
        }
        try {
            //创建 Java 源文件
            JavaFileObject javaFileObject = filer.createSourceFile(getClassName(classAllName));
            //写入代码
            createSourceFile(classAllName, fieldMap, javaFileObject.openWriter());
            //手动编译
            cpmpile(javaFileObject.toUri().getPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        // return false; false 会使编译中止
        return true;
    }

    /**
     * 读取包名
     * @param name
     * @return
     */
    private String getPackage(String name) {
        String result = name;
        if (result.contains(SEPARATOR)) {
            result = name.substring(0, result.lastIndexOf(SEPARATOR));
        } else {
            result = "";
        }
        return result;
    }

    /**
     * 读取类名
     * @param name
     * @return
     */
    private String getClassName(String name) {
        String result = name;
        if (result.contains(SEPARATOR)) {
            result = name.substring(result.lastIndexOf(SEPARATOR) + 1);
        }
        return result;
    }

    /**
     * 驼峰命名
     * @param name
     * @return
     */
    private String humpName(String name) {
        String result = name;
        if (name.length() == 1) {
            result = name.toUpperCase();
        } else {
            result = name.substring(0, 1).toUpperCase() + name.substring(1);
        }
        return result;
    }

    /**
     * 编译文件
     * @param path
     */
    private void cpmpile(String path) throws IOException {
        //获取编译器
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        //获取文件管理者
        StandardJavaFileManager javaFileManager = compiler.getStandardFileManager(null, null, null);
        //获取文件
        Iterable file = javaFileManager.getJavaFileObjects(path);
        //编译任务
        JavaCompiler.CompilationTask task = compiler.getTask(null, javaFileManager, null, null, null, file);
        //开始编译
        task.call();
        //关闭文件管理者
        javaFileManager.close();
    }

    /**
     * 生成源文件
     * @param className
     * @param fieldMap
     * @param writer
     */
    private void createSourceFile(String className, Map<TypeMirror, Name> fieldMap, Writer writer) throws IOException {
        JavaWriter javaWriter = new JavaWriter(writer);
        //包名
        javaWriter.emitPackage(getPackage(className));
        //类名
        javaWriter.beginType(getClassName(className), "class", EnumSet.of(Modifier.PUBLIC));
        //属性
        for (Map.Entry<TypeMirror, Name> entry : fieldMap.entrySet()) {
            String type = entry.getKey().toString();
            String name = entry.getValue().toString();
            javaWriter.emitField(type, name, EnumSet.of(Modifier.PRIVATE));
        }
        //Getter and Setter
        for (Map.Entry<TypeMirror, Name> entry : fieldMap.entrySet()) {
            String type = entry.getKey().toString();
            String name = entry.getValue().toString();
            javaWriter.beginMethod(type, "get" + humpName(name), EnumSet.of(Modifier.PUBLIC))
                    .emitStatement("return" + name)
                    .endMethod();
            javaWriter.beginMethod("void", "set" + humpName(name), EnumSet.of(Modifier.PUBLIC), type, "arg")
                    .emitStatement("this." + name + "= arg")
                    .endMethod();
        }
        javaWriter.endType().close();
    }
}
