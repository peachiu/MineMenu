package com.minemenu.core;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/** Delayed and repeating jobs on the server thread (meteors, tornadoes, Chaos events...). Cleared with each world. */
public final class ServerTasks {
    private record Task(String name, long runAt, Predicate<MinecraftServer> body) {
    }

    private static final List<Task> tasks = new ArrayList<>();
    private static long now;

    private ServerTasks() {
    }

    /** Runs once after {@code delayTicks}. */
    public static void later(String name, int delayTicks, java.util.function.Consumer<MinecraftServer> body) {
        tasks.add(new Task(name, now + delayTicks, server -> {
            body.accept(server);
            return false;
        }));
    }

    /** Runs every tick from now on while {@code body} returns true. */
    public static void repeat(String name, Predicate<MinecraftServer> body) {
        tasks.add(new Task(name, now, body));
    }

    public static void clear() {
        tasks.clear();
    }

    public static void tick(MinecraftServer server) {
        now++;
        if (tasks.isEmpty()) {
            return;
        }
        List<Task> due = new ArrayList<>();
        Iterator<Task> it = tasks.iterator();
        while (it.hasNext()) {
            Task task = it.next();
            if (task.runAt() <= now) {
                it.remove();
                due.add(task);
            }
        }
        for (Task task : due) {
            boolean[] again = {false};
            Guard.run(task.name(), () -> again[0] = task.body().test(server));
            if (again[0]) {
                tasks.add(new Task(task.name(), now + 1, task.body()));
            }
        }
    }
}
