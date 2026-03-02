package com.example.demoworkflow.utils.fs;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

/**
 * 文件系统基础组件，使用相关方法时，切记对IOException进行判断。
 */
@Component
@Slf4j
public class FSUtilsBase {
    // 虽然这里是这么命名的，但为了防止污染Jar包所在路径，这里实际上嵌套了一层`data`文件夹
    public Path jarPath = null;

    public String workPath = "base";

    public String suffix = ".data";

    public Path getJarDirectory() throws URISyntaxException, IOException {
        // 获取当前类所在的 CodeSource 位置
        var location = FSUtilsBase.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        // 转换为 Path 对象
        Path path = Paths.get(location);
        // 如果指向的是文件（JAR），返回其父目录；如果指向目录（如 classes），则直接返回
        Path dataPath;
        if (Files.isRegularFile(path))
            dataPath = path.getParent().resolve("data");
        else
            dataPath = path.resolve("data");
        if(!Files.exists(dataPath)) Files.createDirectory(dataPath);
        return dataPath;
    }

    public void initWorkPath() throws IOException {
        Path basePath = jarPath.resolve(workPath);
        if(!Files.exists(basePath)){
            Files.createDirectory(basePath);
        }
    }

    public FSUtilsBase(){
        try {
            jarPath = getJarDirectory();
            initWorkPath();
        } catch (URISyntaxException e) {
            log.error("获取Jar包位置并创建`data`文件夹时出错：\n{}", e.getMessage());
        } catch (IOException e) {
            log.error("创建工作文件夹时出错：\n{}", e.getMessage());
        } catch(Exception e){
            log.error("文件系统初始化时出错：\n{}", e.getMessage());
        }
    }

    public Path pathFactory(String filename) throws IOException {
        Path wp = jarPath.resolve(workPath);
        if(!Files.exists(wp)){
            Files.createDirectory(wp);
        }
        return wp.resolve(filename + suffix);
    }

    public Path pathFactory(Path path) throws IOException {
        Path wp = jarPath.resolve(workPath);
        if(!Files.exists(wp)){
            Files.createDirectory(wp);
        }
        return wp.resolve(wp);
    }

    public String read(String filename) throws IOException{
        Path filePath = pathFactory(filename);
        if(!Files.exists(filePath)){
            return null;
        }
        return Files.readString(filePath);
    }

    public void write(String filename, String content) throws IOException{
        Path filePath = pathFactory(filename);
        if(!Files.exists(filePath)){
            Files.createFile(filePath);
        }
        Files.writeString(filePath, content);
    }

    public void write(String filename, Object obj) throws IOException{
        Path filePath = pathFactory(filename);
        if(!Files.exists(filePath)){
            Files.createFile(filePath);
        }
        Files.writeString(filePath, JSON.toJSONString(obj, JSONWriter.Feature.PrettyFormat));
    }

    public void delete(String filename) throws IOException{
        Path filePath = pathFactory(filename);
        Files.deleteIfExists(filePath);
    }

    public void deleteWorkDirectory() throws IOException{
        Path wp = jarPath.resolve(workPath);
        try(var stream = Files.walk(wp)){
            stream.sorted(Comparator.reverseOrder())
                    .forEach(path -> path.toFile().delete());
        }
        Files.deleteIfExists(wp);
    }

    public void update(String filename, String content) throws IOException{
        Path filePath = pathFactory(filename);
        if(!Files.exists(filePath)) return;
        Files.writeString(filePath, content);
    }

    public void appendEnd(String filename, String content) throws IOException{
        String oldContent = read(filename);
        String newContent = oldContent + content;
        write(filename, newContent);
    }
}
