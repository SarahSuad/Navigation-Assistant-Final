![alt text](https://assetsds.cdnedge.bluemix.net/sites/default/files/beta2/uploads/2013/03/NSU1.jpg)
# Navigational Assistance for Visually Impaired Using Computer Vision

 Blind people face many difficulties in daily life, one of which is navigation. There are several solutions leveraging the use of computer hardware and artificial intelligence to help guide them. However, most current solutions use complicated hardware and so are not suitable for everyone. This project uses deep learning to implement a **semantic segmentation** algorithm that recognizes walkable areas in an interior environment in real-time, directing users away from obstacles such as furniture or people. We test **ShuffleNet** and **DeepLabv3** and implement the former into an into an app that can be used on any android phone.


## Group: 07       course: cse499.11


|                |ID                          |Email                         |
|----------------|-------------------------------|-----------------------------|
|Ishrat Jahan Ananya|1631636042             |ishrat.jahan16@northsouth.edu        |
|Shadab Hafiz Chowdhury         |1631335642            |shadab.choudhury@northsouth.edu           |
|Nabiul Hoque Khandakar         |1631164642            |nabiul.khandakar@northsouth.edu           |
|Sarah Suad         |1632282642            |Sarah.suad@northsouth.edu           |


<!-- TABLE OF CONTENTS -->
## Table of Contents

* [About the Project](#about-the-project)
* [Dataset](#dataset)
* [Disclaimer](#disclaimer)


# About the Project
The goal of this research is to use purely computer vision to help a blind person gain a rudimentary understanding of an interior area’s layout, allowing them to plan out how to proceed. This would be a significant step in making moving around easier for them. Thhe solution consisted of the following steps :
* Firstly, a mobile app that passes image frames to a computer vision algorithm continuously at a given rate/frames per second.
* Second, a semantic segmentation algorithm that takes the passed frames and converts them to a segmented image where different classes of objects are detected and assigned a pixel colour.
* Finally, an output function that ‘reads’ the segmented image and checks for walkable space or blocked space in areas where the user may walk. It then transmits this information to the user in the form of audio through text-to-speech.


# Dataset
The primary dataset to use in this project is the [MIT ADE20k Dataset](http://sceneparsing.csail.mit.edu/) for Scene Segmentation. This dataset features 20,120 images taken from a wide variety of scenes both outdoors and indoors. 

The ADE20k Dataset features a total of 150 Classes. However, most of these classes are either superfluous, or too finely detailed, for the task at hand. Therefore, the class labels were consolidated into the primary classes that will be applicable to the process of interior navigation. The consolidated class labels are given below:

* 1 (wall) <- 9 (window), 15 (door), 33 (fence), 43 (pillar), 44 (sign board), 145 (bulletin board)
* 4 (floor) <- 7 (road), 14 (ground, 30 (field), 53 (path), 55 (runway))
* 5 (tree) <- 18 (plant)
* 8 (furniture) <- 8 (bed), 11 (cabinet), 14 (sofa), 16 (table), 19 (curtain), 20 (chair), 25 (shelf), 34 (desk) 
* 7 (stairs) <- 54 (stairs)
* 26 (others) <- Class number larger than 26

# Disclaimer
