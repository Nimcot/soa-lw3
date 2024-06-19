package com.example.lw3.controllers;

import com.example.lw3.Lw3ApplicationConfig;
import com.example.lw3.dto.FileInfo;

import java.util.*;
import java.text.SimpleDateFormat;
import java.io.BufferedInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaTypeFactory;

import com.azure.storage.file.share.*;
import com.azure.storage.common.ParallelTransferOptions;


@Controller
public class Home {

  private final Lw3ApplicationConfig config;

  public Home(Lw3ApplicationConfig config) {
    this.config = config;
  }

  private ShareDirectoryClient getStorageDir() {
    ShareDirectoryClient dirClient = new ShareFileClientBuilder()
        .connectionString(config.get("storage.connection-string")).shareName(config.get("storage.share"))
        .resourcePath(config.get("storage.directory"))
        .buildDirectoryClient();
    try {
      dirClient.create();
    }
    catch (Exception ignored) {}
    return dirClient;
  }

  @GetMapping({"", "/"})
  public String listFiles(Model model) {
    var dir = getStorageDir();
    if (dir == null)
      return "index";
    var files = new ArrayList<FileInfo>();
    dir.listFilesAndDirectories().forEach(item -> {
      if (item.isDirectory()) return;
      try {
        var fileClient = dir.getFileClient(item.getName());
        var properties = fileClient.getProperties();
        var creationTimestamp = new Date(properties.getSmbProperties().getFileCreationTime().toInstant().toEpochMilli());
        var editTimestamp = new Date(properties.getSmbProperties().getFileChangeTime().toInstant().toEpochMilli());
        var dateFormat = new SimpleDateFormat("yyyy/MM/dd");
        var timeFormat = new SimpleDateFormat("HH:mm:ss.S");
        files.add(new FileInfo() {{
          name = item.getName();
          size = formatBytes(item.getFileSize());
          creationDate = dateFormat.format(creationTimestamp);
          creationTime = timeFormat.format(creationTimestamp);
          editDate = dateFormat.format(editTimestamp);
          editTime = timeFormat.format(editTimestamp);
        }});
      } catch (Exception ignored) {}
    });
    model.addAttribute("files", files);
    return "index";
  }

  @GetMapping("/files/{file_name}")
  @ResponseBody
  public void downloadFile(@PathVariable("file_name") String fileName, HttpServletResponse response) {
    var dir = getStorageDir();
    if (dir == null)
      return;
    try {
      response.reset();
      response.setCharacterEncoding("UTF-8");
      response.setContentType(MediaTypeFactory.getMediaType(fileName).orElseThrow().toString());
      response.setHeader("Content-Disposition", "attachment;filename=\"" +
          URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20") + '\"');
      var fileClient = dir.getFileClient(fileName);
      fileClient.download(response.getOutputStream());
      response.flushBuffer();
    } catch (Exception ignored) {}
  }

  @PostMapping({"", "/"})
  public String uploadFile(@RequestBody MultipartFile file) {
    var dir = getStorageDir();
    if (dir == null)
      return "redirect:/";
    ShareFileClient fileClient = null;
    try(var inputStream = new BufferedInputStream(file.getInputStream())) {
      fileClient = dir.getFileClient(file.getOriginalFilename());
      fileClient.create(file.getSize());
      fileClient.upload(inputStream, file.getSize(), new ParallelTransferOptions(3145728, 10, null, 3145728));
    } catch (Exception ignored) {
      if (fileClient != null && fileClient.exists())
        fileClient.delete();
    }
    return "redirect:/";
  }

  public static String formatBytes(long amount) {
    if (amount < 1024)
      return amount + " B";
    int exp = (int) (Math.log(amount) / Math.log(1024));
    return String.format(Locale.US, "%.2f %siB", amount / Math.pow(1024, exp), ("KMGT").charAt(exp-1));
  }

}