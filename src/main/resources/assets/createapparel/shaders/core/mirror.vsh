#version 150

in vec3 Position;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 ReflectionMatrix;
out vec4 reflectionPosition;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    reflectionPosition = ReflectionMatrix * vec4(Position, 1.0);
}
