#version 150

uniform sampler2D Sampler0;
uniform vec4 ReflectionBounds;
in vec4 reflectionPosition;
out vec4 fragColor;

void main() {
    if (reflectionPosition.w <= 0.0) discard;
    vec2 uv = reflectionPosition.xy / reflectionPosition.w * 0.5 + 0.5;
    uv = clamp(uv, ReflectionBounds.xy, ReflectionBounds.zw);
    fragColor = vec4(texture(Sampler0, uv).rgb * 0.96, 1.0);
}
