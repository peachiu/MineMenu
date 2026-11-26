#version 150

in vec4 vertexColor;
in float vertexDistance;

uniform vec4 ColorModulator;
uniform vec2 FadeRange;

out vec4 fragColor;

// MineXray ESP: vertex colour, faded with distance (FadeRange = start, end; end <= start = no fade).
void main() {
    vec4 color = vertexColor * ColorModulator;
    if (FadeRange.y > FadeRange.x) {
        color.a *= 1.0 - 0.75 * smoothstep(FadeRange.x, FadeRange.y, vertexDistance);
    }
    if (color.a < 0.01) {
        discard;
    }
    fragColor = color;
}
