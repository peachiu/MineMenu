#version 150

// Contrast Adaptive Sharpening (after AMD FidelityFX CAS, MIT license), used by MineBoost's Render Scale:
// the world is drawn smaller, this stretches it to the window and sharpens it in the same pass. Edges that
// are already contrasty are sharpened less, so it doesn't ring or halo like a plain sharpen filter.

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;      // size of the smaller world image, in pixels
uniform float Sharpness;  // 0 = none, 1 = most

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 px = 1.0 / InSize;
    vec3 a = texture(DiffuseSampler, texCoord + vec2(-px.x, -px.y)).rgb;
    vec3 b = texture(DiffuseSampler, texCoord + vec2(0.0, -px.y)).rgb;
    vec3 c = texture(DiffuseSampler, texCoord + vec2(px.x, -px.y)).rgb;
    vec3 d = texture(DiffuseSampler, texCoord + vec2(-px.x, 0.0)).rgb;
    vec3 e = texture(DiffuseSampler, texCoord).rgb;
    vec3 f = texture(DiffuseSampler, texCoord + vec2(px.x, 0.0)).rgb;
    vec3 g = texture(DiffuseSampler, texCoord + vec2(-px.x, px.y)).rgb;
    vec3 h = texture(DiffuseSampler, texCoord + vec2(0.0, px.y)).rgb;
    vec3 i = texture(DiffuseSampler, texCoord + vec2(px.x, px.y)).rgb;

    // Soft min and max of the neighbourhood (cross + corners).
    vec3 mn = min(min(min(d, e), min(f, b)), h);
    vec3 mn2 = min(mn, min(min(a, c), min(g, i)));
    mn += mn2;
    vec3 mx = max(max(max(d, e), max(f, b)), h);
    vec3 mx2 = max(mx, max(max(a, c), max(g, i)));
    mx += mx2;

    // How much room there is to sharpen without clipping.
    vec3 amp = clamp(min(mn, 2.0 - mx) / max(mx, vec3(1.0e-5)), 1.0e-4, 1.0);
    amp = inversesqrt(amp);

    float peak = 8.0 - 3.0 * clamp(Sharpness, 0.0, 1.0);
    vec3 w = -1.0 / (amp * peak);
    vec3 window = (b + d) + (f + h);
    vec3 color = clamp((window * w + e) / (4.0 * w + 1.0), 0.0, 1.0);
    fragColor = vec4(color, 1.0);
}
