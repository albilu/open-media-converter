package org.omc.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import org.omc.core.ProgressCallback;
import org.omc.model.AudioSettings;

import static org.junit.jupiter.api.Assertions.*;

/** Uses a real encoder when probing cannot provide a duration or frame total. */
@Timeout(20)
class UnmeasurableMediaProgressTest {
    @TempDir Path root;

    @Test
    void ffmpegWithoutDurationOrFrameCountReportsUnknownActivity() throws Exception {
        Path input = root.resolve("input.wav"), output = root.resolve("output.mp3");
        Process fixture = new ProcessBuilder("/usr/bin/ffmpeg", "-v", "error", "-f", "lavfi", "-i",
                "sine=frequency=440:duration=2", input.toString()).start();
        try {
            assertTrue(fixture.waitFor(5, TimeUnit.SECONDS));
            assertEquals(0, fixture.exitValue());
        } finally { fixture.destroyForcibly(); }
        Path probe = Files.writeString(root.resolve("unknown-ffprobe"), "#!/bin/sh\nprintf 'N/A\\n'\n");
        Path encoder = Files.writeString(root.resolve("paced-ffmpeg"), "#!/bin/sh\nexec /usr/bin/ffmpeg -re \"$@\"\n");
        assertTrue(probe.toFile().setExecutable(true));
        assertTrue(encoder.toFile().setExecutable(true));
        var events = new CopyOnWriteArrayList<Double>();
        var result = new FFmpegService(encoder, probe).convertAudio(input, output,
                AudioSettings.builder().codec("MP3").bitrate(128).build(), (percentage, bytes, speed) -> {
                    events.add(percentage);
                    assertEquals(0, bytes);
                    assertEquals(0, speed);
                });
        assertTrue(result.success(), result.toString());
        assertTrue(Files.size(output) > 0);
        assertTrue(events.size() >= 3, events.toString());
        assertTrue(events.stream().allMatch(p -> p == ProgressCallback.INDETERMINATE), events.toString());
        Process verify = new ProcessBuilder("/usr/bin/ffmpeg", "-v", "error", "-i", output.toString(), "-f", "null", "-").start();
        try {
            assertTrue(verify.waitFor(5, TimeUnit.SECONDS));
            assertEquals(0, verify.exitValue());
        } finally { verify.destroyForcibly(); }
    }
}
