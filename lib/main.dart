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
  final platformDownload = MethodChannel('com.example.spring/download');
  final platformShare = MethodChannel('com.example.spring/share');

  String status = 'Enter a YouTube URL';
  bool _isLoading = false;

  @override
  void initState() {
    super.initState();
    _getSharedText();
  }

  Future<void> _getSharedText() async {
    try {
      final sharedText = await platformShare.invokeMethod<String>('getSharedText');
      if (sharedText != null && sharedText.isNotEmpty) {
        setState(() {
          _controller.text = sharedText;
          status = 'Received shared URL';
        });
      }
    } on PlatformException catch (e) {
      print("Failed to get shared text: '${e.message}'.");
    }
  }

  Future<void> _startDownload() async {
    if (_controller.text.isEmpty) {
      setState(() {
        status = "Please enter a URL.";
      });
      return;
    }

    setState(() {
      status = "Downloading and setting ringtone...";
      _isLoading = true;
    });

    try {
      final filePath = await platformDownload.invokeMethod('downloadMP3', {'url': _controller.text});
      setState(() {
        // Clean, simple success message with emojis
        status = "✅ Downloaded!\n🔔 Ringtone set!";
        _controller.clear();
      });
    } on PlatformException catch (e) {
      setState(() {
        status = "❌ Failed: ${e.message}";
      });
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Spring Downloader')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            TextField(
              controller: _controller,
              decoration: InputDecoration(
                border: OutlineInputBorder(),
                labelText: "YouTube URL",
                hintText: "https://www.youtube.com/watch?v=...",
              ),
            ),
            SizedBox(height: 20),
            _isLoading
                ? Column(
                    children: [
                      CircularProgressIndicator(),
                      SizedBox(height: 10),
                      Text("Working... Please wait"),
                    ],
                  )
                : ElevatedButton(
                    onPressed: _startDownload,
                    child: Text('Download and Set as Ringtone'),
                  ),
            SizedBox(height: 20),
            Expanded(
              child: SingleChildScrollView(
                child: Text(status),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
