import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await requestStoragePermission();
  runApp(MyApp());
}

Future<void> requestStoragePermission() async {
  var status = await Permission.storage.status;
  if (!status.isGranted) {
    await Permission.storage.request();
  }
}

class MyApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(home: DownloadScreen());
  }
}

class DownloadScreen extends StatefulWidget {
  @override
  _DownloadScreenState createState() => _DownloadScreenState();
}

class _DownloadScreenState extends State<DownloadScreen> {
  final TextEditingController _controller = TextEditingController();
  final platform = MethodChannel('com.example.spring/download');

  String status = 'Enter a YouTube URL';

  Future<void> _startDownload() async {
    if (_controller.text.isEmpty) {
      setState(() {
        status = "Please enter a URL.";
      });
      return;
    }

    setState(() {
      status = "Starting download...";
    });

    try {
      final filePath =
          await platform.invokeMethod('downloadMP3', {'url': _controller.text});
      setState(() {
        status = "Download complete!\nSaved at:\n$filePath";
      });
    } on PlatformException catch (e) {
      setState(() {
        status = "Failed: ${e.message}";
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
        appBar: AppBar(title: Text('YouTube to MP3 Downloader')),
        body: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            children: [
              TextField(
                controller: _controller,
                decoration: InputDecoration(
                    border: OutlineInputBorder(),
                    labelText: "YouTube URL",
                    hintText: "https://www.youtube.com/watch?v=..."),
              ),
              SizedBox(height: 20),
              ElevatedButton(
                onPressed: _startDownload,
                child: Text('Download as MP3'),
              ),
              SizedBox(height: 20),
              Expanded(child: SingleChildScrollView(child: Text(status))),
            ],
          ),
        ));
  }
}
