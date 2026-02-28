//package com.parul.processing.karaoke.unused;
//
//import java.awt.Font;
//import java.io.File;
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.nio.file.StandardOpenOption;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Map;
//import java.util.TreeMap;
//import java.util.stream.Collectors;
//
//import org.apache.commons.io.FileUtils;
//import org.springframework.http.MediaType;
//import org.springframework.web.client.RestClient;
//
//import com.hamoid.VideoExport;
//import com.parul.karaoke.converter.AssToTextConverter;
//import com.parul.karaoke.runner.FFMpegRunner;
//import com.parul.karaoke.runner.KaraokeRunner;
//import com.parul.karaoke.runner.PythonRunner;
//import com.parul.karaoke.runner.VideoStitcherRunner;
//
//import processing.core.PApplet;
//import processing.core.PFont;
//
//public class RunnerAnimation extends PApplet {
//	
//	public static final String FOLDER = "C:\\development\\karaoke\\Stories\\ThirstyCrow\\";
//	public static final String LANGUAGE = "ENGLISH";
//	public static final String LANG = "hi";
//	public static final String SPEAKER = "voice_preview_rowan__gentle__soft-spoken__warm.mp3";
////	public static final String SPEAKER = "voice_preview_viraj_-_rich_and_soft.mp3";
//	public static final String FONT = "Hey Comic.ttf";
////	public static final String FONT = "TiroDevanagariHindi-Regular.ttf";
//	public static final String SPEED = "0.80";
//	
//	public static final String VOCALS_ORG_FILE_NAME = "vocals_org.mp3";
//	public static final String VOCALS_FILE_NAME = "vocals.mp3";
//	public static final String RAW_LYRICS_FILE_NAME = "raw_lyrics.txt";
//	public static final String RAW_LYRICS_FILE = FOLDER + RAW_LYRICS_FILE_NAME;
//	public static final String ASS_FILE_NAME = "lyrics.ass";
//	public static final String LYRICS_FILE_NAME = "lyrics.txt";
//	public static final String BREAK = "BREAK";
//	public static final String DELIMITER = "_";
//	
//	public static final String ALIGN_LYRICS_PYTHON_FILE_NAME = "alignLyrics.py";
//	public static final String CMD_FILE_NAME = "runAlignLyrics.bat";
//	public static final String KARAOKE_FILE_NAME = "karaoke.mp4";
//	
//	public static final String PAYLOAD = "{\"text\":\"%s\",\"temperature\":0.8,\"exaggeration\":0.5,\"cfg_weight\":0.5,\"speed_factor\":1,\"seed\":2000,\"language\":\"en\",\"voice_mode\":\"predefined\",\"split_text\":true,\"chunk_size\":120,\"output_format\":\"mp3\",\"predefined_voice_id\":\"" + SPEAKER + "\"}";
////	public static final String PAYLOAD = "{\"text\":\"%s\",\"temperature\":0.78,\"exaggeration\":1.1,\"cfg_weight\":0.55,\"speed_factor\":1,\"seed\":2000,\"language\":\""+ LANG +"\",\"voice_mode\":\"clone\",\"split_text\":true,\"chunk_size\":120,\"output_format\":\"mp3\",\"reference_audio_filename\":\""+ SPEAKER + "\"}";
//	
//	private final PythonRunner pythonRunner = new PythonRunner();
//	private final AssToTextConverter assToTextConverter = new AssToTextConverter();
//	private final KaraokeRunnerProcessing karaokeRunner = new KaraokeRunnerProcessing();
//	private final VideoStitcherRunner stitcher = new VideoStitcherRunner();
//	private final FFMpegRunner ffMpegRunner = new FFMpegRunner();
//	private VideoExport videoExport;
//
//    public static void main(String[] args) {
//        PApplet.main("com.parul.karaoke.animation.RunnerAnimation");
//    }
//
//    public void settings(){
//        size(1920,1080);
//    }
//
//    public void setup(){
//    	background(0);
//    	frameRate(1);
//    	
////    	videoExport = new VideoExport(this, "C:\\development\\test\\karaokeTest.mp4"); //
////    	videoExport.setFfmpegPath("C:\\development\\ffmpeg\\ffmpeg-master-latest-win64-gpl-shared\\bin\\ffmpeg.exe");
////    	videoExport.setFrameRate(5);
////    	videoExport.startMovie();
//    	noLoop();
//    }
//
//    public void draw(){
//    	
//    	try {
//			Map<String, List<String>> chunks = createChunks();
//			chunks.forEach((k, v) -> System.out.println(k + ":" + v + ":" + v.size()));
//			for (String key : chunks.keySet()) {
//				List<String> chunk = chunks.get(key);
//	//			runner.callTTSAndSaveAudio(key, chunk);
//	//			runner.controlSpeed(key, SPEED);
//	//			runner.alignLyrics(key, chunk);
//	//			runner.genLyricsFile(key);
//				runKaraoke(key, chunk.size());
//			}
//	//		List<String> parts = chunks.entrySet().stream().map(e -> FOLDER + e.getKey() + KARAOKE_FILE_NAME).collect(Collectors.toList());
//	//		runner.stitchKaraokes(parts);
//	//		runner.cleanup();
//    	} catch (Exception e) {
//			e.printStackTrace();
//		}
//
//	    // Capture the current frame
////	    videoExport.saveFrame();
//    }
//    
//    private Map<String, List<String>> createChunks() throws Exception {
//		Map<String, List<String>> chunks = new TreeMap<>();
//		File rawLyrics = new File(RAW_LYRICS_FILE); 
//		List<String> lines = FileUtils.readLines(rawLyrics);
//		int i = 0;
//		while (i < lines.size()) {
//			String line = lines.get(i).trim();
//			String[] split = line.split(DELIMITER);
//			if (i == lines.size() - 1) {
//				break;
//			}
//			i++;
//			line = lines.get(i).trim();
//			List<String> chunk = new ArrayList<>();
//			while (!line.startsWith(BREAK)) {
//				if (!line.isEmpty()) {
//					chunk.add(line);
//				}
//				if (i == lines.size() - 1) {
//					break;
//				}
//				i++;
//				line = lines.get(i).trim();
//			}
//			chunks.put(split[1] + DELIMITER, chunk);
//		}
//		return chunks;
//	}
//	
//	public void callTTSAndSaveAudio(String key, List<String> list) throws Exception {
//		String json =  String.format(PAYLOAD, String.join("\\n", list));
//		 byte[] data = RestClient.create("http://localhost:8000/tts")
//						        .post()
//						        .header("User-Agent", "PostmanRuntime/7.51.1")
//						        .contentType(MediaType.APPLICATION_JSON)
//						        .body(json)
//						        .accept(MediaType.ALL)
//						        .retrieve()
//						        .body(byte[].class);
//		 Files.write(Paths.get(FOLDER + key + VOCALS_ORG_FILE_NAME), data, StandardOpenOption.CREATE);
//		 Files.write(Paths.get(FOLDER + key + RAW_LYRICS_FILE_NAME), list, StandardOpenOption.CREATE);
//	}
//	
//	private void controlSpeed(String key, String factor) throws Exception {
//		ffMpegRunner.run(new String[]{
//                "ffmpeg",
//                "-i", FOLDER + key + VOCALS_ORG_FILE_NAME,
//                "-af", "arnndn=model.rnnn",
//                "-filter:a", "atempo=" + factor,
//                FOLDER + key + VOCALS_FILE_NAME
//            }, "Altering Speed");
//	}
//	
//	public boolean alignLyrics(String key, List<String> list) throws Exception {
//		String pythonCmd = Files.readString(Paths.get("src/main/resources/" + ALIGN_LYRICS_PYTHON_FILE_NAME));
//		pythonCmd = pythonCmd.replace("LANGUAGE", LANGUAGE);
//		pythonCmd = pythonCmd.replace("FOLDER", FOLDER);
//		pythonCmd = pythonCmd.replace("KEY", key);
//		pythonCmd = pythonCmd.replace("RAW_FILE_NAME", RAW_LYRICS_FILE_NAME);
//		pythonCmd = pythonCmd.replace("VOCALS_FILE_NAME", VOCALS_FILE_NAME);
//		pythonCmd = pythonCmd.replace("ASS_FILE_NAME", ASS_FILE_NAME);
//		pythonCmd = pythonCmd.replace("\\", "/");
//		Path pythonFile = Paths.get(FOLDER + key + ALIGN_LYRICS_PYTHON_FILE_NAME);
//		Files.write(pythonFile, pythonCmd.getBytes(), StandardOpenOption.CREATE);
//		
//		StringBuilder sb = new StringBuilder();
//		sb.append("where python" + "\n");
//		String cmd = "python " + pythonFile.toString();
//		sb.append(cmd);
//		Path cmdFile = Paths.get(FOLDER + key + CMD_FILE_NAME);
//		Files.write(cmdFile, sb.toString().getBytes(), StandardOpenOption.CREATE);
//		
//		return pythonRunner.runPython(cmdFile.toString(), "generated");
//	}
//	
//	@SuppressWarnings("deprecation")
//	public void genLyricsFile(String key) throws IOException {
//		List<String> convert = assToTextConverter.convert(FileUtils.readLines(new File(FOLDER + key + ASS_FILE_NAME)));
//		Files.write(Path.of(FOLDER + key + LYRICS_FILE_NAME), convert, StandardOpenOption.CREATE);
//	}
//	
//	public void runKaraoke(String key, int size) throws Exception {
//		String introPath = "src/main/resources/introBalloons.mp4";
//	    String textHtmlPath = null;
//	    PFont font = createFont("Arial", 32);
//		karaokeRunner.run(this, FOLDER, VOCALS_FILE_NAME, LYRICS_FILE_NAME, KARAOKE_FILE_NAME,  key, null, null, font, size);
//		
//	}
//	
//	private void stitchKaraokes(List<String> karaokeParts) throws Exception {
//		stitcher.stitchAllParts(
//               null, null, karaokeParts, FOLDER + KARAOKE_FILE_NAME);
//	}
//	
//	public void cleanup() throws Exception {
//		List<Path> toBeDeleted = Files.list(Paths.get(FOLDER))
//			.filter(p -> !p.getFileName().startsWith(RAW_LYRICS_FILE_NAME) && !p.getFileName().startsWith(KARAOKE_FILE_NAME))
//			.collect(Collectors.toList());
//		System.out.println("Cleaning up");
//		for (Path p : toBeDeleted) {
//			System.out.println("Deleting: " + p.toString());
//			Files.delete(p);
//		}
//		System.out.println();
//	}
//
//}