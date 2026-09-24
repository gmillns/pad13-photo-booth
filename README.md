# Pad 13 Photo Booth v0.4.1
Camera transition hotfix for v0.4.

- preserves the working Baring Ladies Festival welcome/gallery interface
- CameraX is no longer bound while the PreviewView is hidden at startup
- pressing TAKE PHOTO first reveals the PreviewView, then explicitly binds the camera
- gallery rotation, 30-second inactivity return, capture, Retake/Keep and local saving retained
