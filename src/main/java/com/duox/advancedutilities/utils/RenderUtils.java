package com.duox.advancedutilities.utils;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;

public class RenderUtils {

    /**
     * Helper method to add a filled box to the vertex buffer.
     */
    public static void addFilledBoxToBuffer(PoseStack stack, VertexConsumer buffer, AABB box, float r, float g, float b, float a) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        Matrix4f matrix = stack.last().pose();

        // Down
        buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a);

        // Up
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a);

        // North
        buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a);

        // South
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a);

        // West
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a);

        // East
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a);
    }

    public static void addLineBoxToBuffer(PoseStack stack, VertexConsumer buffer, AABB box, float r, float g, float b, float a) {
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;
        Matrix4f matrix = stack.last().pose();

        // Bottom
        buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a).setNormal(0, 1, 0);

        // Top
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a).setNormal(0, 1, 0);

        // Sides
        buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a).setNormal(0, 1, 0);
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a).setNormal(0, 1, 0);
    }
}
