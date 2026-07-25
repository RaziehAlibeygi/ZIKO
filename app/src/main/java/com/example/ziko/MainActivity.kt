package com.example.ziko

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ExitToApp // Added for Exit icon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.room.*
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// --- Sound Effect Helper ---
fun playSoundEffect(context: Context, soundResourceId: Int) {
    val mediaPlayer = MediaPlayer.create(context, soundResourceId)
    mediaPlayer?.setOnCompletionListener { mp -> mp.release() }
    mediaPlayer?.start()
}

// --- Navigation Routes ---
sealed class Screen(val route: String, val title: String? = null) {
    object Startup : Screen("startup_screen")
    object StudentManagement : Screen("student_management_screen", "Student Management")
    object Settings : Screen("settings_screen", "Settings")
    object About : Screen("about_screen", "About")
}

// --- Room: TypeConverter for Uri ---
class UriTypeConverter {
    @TypeConverter
    fun fromString(value: String?): Uri? = value?.let { Uri.parse(it) }

    @TypeConverter
    fun toString(uri: Uri?): String? = uri?.toString()
}

// --- Room: Entity ---
@Entity(tableName = "students")
@TypeConverters(UriTypeConverter::class)
data class Student(
    @PrimaryKey val id: String,
    val fullName: String,
    val studentId: String,
    val major: String,
    val degreeLevel: String,
    val imageUri: Uri? = null
)

// --- Room: DAO ---
@Dao
interface StudentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: Student)

    @Delete
    suspend fun deleteStudent(student: Student)

    @Query("SELECT * FROM students ORDER BY fullName ASC")
    fun getAllStudents(): Flow<List<Student>>
}

// --- Room: Database ---
@Database(entities = [Student::class], version = 1, exportSchema = false)
@TypeConverters(UriTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "student_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// --- Repository ---
class StudentRepository(private val studentDao: StudentDao) {
    val allStudents: Flow<List<Student>> = studentDao.getAllStudents()
    suspend fun insert(student: Student) = studentDao.insertStudent(student)
    suspend fun delete(student: Student) = studentDao.deleteStudent(student)
}

// --- ViewModel ---
class StudentViewModel(private val repository: StudentRepository) : ViewModel() {
    val allStudents: Flow<List<Student>> = repository.allStudents

    fun addStudent(student: Student) = viewModelScope.launch {
        repository.insert(student)
    }

    fun deleteStudent(student: Student) = viewModelScope.launch {
        repository.delete(student)
    }
}

class StudentViewModelFactory(private val repository: StudentRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StudentViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StudentViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// --- Custom Theme ---
private val TealPrimary = Color(0xFF00796B)
private val TealPrimaryVariant = Color(0xFF004D40)
private val TealSecondary = Color(0xFF26A69A)
private val TealBackground = Color(0xFFE0F2F1)
private val TealSurface = Color(0xFFFFFFFF)
private val TealError = Color(0xFFD32F2F)
private val TealOnPrimary = Color.White
private val TealOnSecondary = Color.Black
private val TealOnBackground = Color.Black
private val TealOnSurface = Color.Black
private val TealOnError = Color.White

@Composable
fun TealTheme(content: @Composable () -> Unit) {
    val colorScheme = lightColorScheme(
        primary = TealPrimary, primaryContainer = TealPrimaryVariant, secondary = TealSecondary,
        background = TealBackground, surface = TealSurface, error = TealError,
        onPrimary = TealOnPrimary, onSecondary = TealOnSecondary, onBackground = TealOnBackground,
        onSurface = TealOnSurface, onError = TealOnError
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}

// --- MainActivity ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TealTheme {
                AppNavigation()
            }
        }
    }
}

// --- Navigation Host ---
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Startup.route) {
        composable(Screen.Startup.route) { StartupScreen(navController) }
        composable(Screen.StudentManagement.route) { StudentManagementScreen(navController) }
        composable(Screen.Settings.route) { SettingsScreen(navController) }
        composable(Screen.About.route) { AboutScreen(navController) }
    }
}

// --- Screens ---
@Composable
fun StartupScreen(navController: NavController) {
    val context = LocalContext.current
    Box(modifier = Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.startup_background), "Background", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Button(
            onClick = {
                playSoundEffect(context, R.raw.button_click)
                navController.navigate(Screen.StudentManagement.route) {
                    popUpTo(Screen.Startup.route) { inclusive = true }
                    launchSingleTop = true
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp).fillMaxWidth(0.8f),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) { Text("Create Student ID Card", color = MaterialTheme.colorScheme.onPrimary, fontSize = 18.sp) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentManagementScreen(navController: NavController) {
    val context = LocalContext.current
    val database = AppDatabase.getDatabase(context.applicationContext)
    val repository = remember { StudentRepository(database.studentDao()) }
    val studentViewModel: StudentViewModel = viewModel(factory = StudentViewModelFactory(repository))

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var isAddingStudent by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route,
                onNavigate = { selectedRoute ->
                    scope.launch { drawerState.close() } // Sound played in AppDrawerContent

                    val currentNavRoute = navController.currentBackStackEntry?.destination?.route

                    if (selectedRoute == Screen.Startup.route) {
                        navController.navigate(selectedRoute) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    } else if (currentNavRoute != selectedRoute) {
                        if (selectedRoute == Screen.StudentManagement.route) {
                            isAddingStudent = false
                        }
                        navController.navigate(selectedRoute) {
                            popUpTo(Screen.StudentManagement.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    } else if (selectedRoute == Screen.StudentManagement.route && currentNavRoute == selectedRoute) {
                        isAddingStudent = false
                    }
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(Screen.StudentManagement.title ?: "Student Management") },
                    navigationIcon = {
                        IconButton(onClick = {
                            playSoundEffect(context, R.raw.button_click)
                            scope.launch { drawerState.open() }
                        }) { Icon(Icons.Filled.Menu, "Open Menu", tint = MaterialTheme.colorScheme.onPrimary) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        ) { paddingValues ->
            StudentManagementContent(
                modifier = Modifier.padding(paddingValues),
                viewModel = studentViewModel,
                isAddingStudent = isAddingStudent,
                onSetIsAddingStudent = { isAddingStudent = it }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentManagementContent(
    modifier: Modifier = Modifier,
    viewModel: StudentViewModel,
    isAddingStudent: Boolean,
    onSetIsAddingStudent: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val studentsListState = viewModel.allStudents.collectAsState(initial = emptyList())
    val students = studentsListState.value

    var fullName by remember(isAddingStudent) { mutableStateOf("") }
    var studentIdText by remember(isAddingStudent) { mutableStateOf("") }
    var major by remember(isAddingStudent) { mutableStateOf("") }
    var degreeLevel by remember(isAddingStudent) { mutableStateOf("Master's") }
    var selectedImageUri by remember(isAddingStudent) { mutableStateOf<Uri?>(null) }
    var nameError by remember(isAddingStudent) { mutableStateOf(false) }
    var idError by remember(isAddingStudent) { mutableStateOf(false) }
    var majorError by remember(isAddingStudent) { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> selectedImageUri = uri }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (isAddingStudent) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), Arrangement.spacedBy(16.dp)) {
                Text("Add New Student", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Box(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 16.dp)) {
                    Card(modifier = Modifier.size(150.dp).clip(CircleShape), onClick = { playSoundEffect(context, R.raw.button_click); imagePicker.launch("image/*") }) {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            if (selectedImageUri != null) {
                                Image(rememberAsyncImagePainter(ImageRequest.Builder(LocalContext.current).data(selectedImageUri).error(R.drawable.error_image).placeholder(R.drawable.placeholder_image).build()), "Student Photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Image(painterResource(R.drawable.placeholder_image), "Add Photo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                Text("Add Photo", Modifier.padding(4.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
                OutlinedTextField(fullName, { fullName = it; nameError = it.isEmpty() }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth(), isError = nameError, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), supportingText = { if (nameError) Text("Name cannot be empty") })
                OutlinedTextField(studentIdText, { studentIdText = it; idError = it.isEmpty() }, label = { Text("Student ID") }, modifier = Modifier.fillMaxWidth(), isError = idError, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next), supportingText = { if (idError) Text("ID cannot be empty") })
                OutlinedTextField(major, { major = it; majorError = it.isEmpty() }, label = { Text("Major") }, modifier = Modifier.fillMaxWidth(), isError = majorError, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), supportingText = { if (majorError) Text("Major cannot be empty") })
                Text("Degree Level:", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onBackground)
                val options = listOf("Bachelor's", "Master's", "Ph.D.")
                Column {
                    options.forEach { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = (degreeLevel == option), onClick = { playSoundEffect(context, R.raw.button_click); degreeLevel = option })
                            Text(
                                text = option,
                                modifier = Modifier.padding(start = 8.dp).clickable { playSoundEffect(context, R.raw.button_click); degreeLevel = option },
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                    OutlinedButton(onClick = {
                        playSoundEffect(context, R.raw.button_click)
                        resetForm({ fullName = it }, { studentIdText = it }, { major = it }, { degreeLevel = it }, { selectedImageUri = it }, { nameError = it }, { idError = it }, { majorError = it })
                    }, modifier = Modifier.weight(1f).padding(end = 4.dp)) { Text("Reset") }
                    Button(onClick = {
                        playSoundEffect(context, R.raw.button_click)
                        nameError = fullName.isEmpty(); idError = studentIdText.isEmpty(); majorError = major.isEmpty()
                        if (!nameError && !idError && !majorError) {
                            if (fullName.isNotBlank() || studentIdText.isNotBlank() || major.isNotBlank()) {
                                viewModel.addStudent(Student(java.util.UUID.randomUUID().toString(), fullName, studentIdText, major, degreeLevel, selectedImageUri))
                                Toast.makeText(context, "$fullName added!", Toast.LENGTH_SHORT).show()
                                resetForm({ fullName = it }, { studentIdText = it }, { major = it }, { degreeLevel = it }, { selectedImageUri = it }, { nameError = it }, { idError = it }, { majorError = it })
                            } else { Toast.makeText(context, "Please fill in details", Toast.LENGTH_SHORT).show() }
                        } else { Toast.makeText(context, "Please correct errors", Toast.LENGTH_SHORT).show() }
                    }, modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) { Text("Add") }
                    Button(onClick = {
                        playSoundEffect(context, R.raw.button_click)
                        onSetIsAddingStudent(false)
                    }, colors = ButtonDefaults.buttonColors(containerColor = TealSecondary), modifier = Modifier.weight(1f).padding(start = 4.dp)) { Text("Done") }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Student List", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                    Button(onClick = {
                        playSoundEffect(context, R.raw.button_click)
                        onSetIsAddingStudent(true)
                    }) { Text("Add New Student") }
                }
                if (students.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) { Text("No students stored yet.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(students, key = { it.id }) { student ->
                            StudentListItem(student) { playSoundEffect(context, R.raw.button_click); viewModel.deleteStudent(student); Toast.makeText(context, "${student.fullName} removed", Toast.LENGTH_SHORT).show() }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawerContent(
    currentRoute: String?,
    onNavigate: (route: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val drawerItems = listOf(
        Triple(Icons.Filled.Home, "Home", Screen.StudentManagement.route),
        Triple(Icons.Filled.Settings, "Settings", Screen.Settings.route),
        Triple(Icons.Filled.Info, "About", Screen.About.route)
    )
    ModalDrawerSheet(modifier.width(280.dp)) {
        Spacer(Modifier.height(16.dp))
        drawerItems.forEach { (icon, label, routeValue) ->
            NavigationDrawerItem(
                icon = { Icon(icon, label) }, label = { Text(label) }, selected = currentRoute == routeValue,
                onClick = {
                    playSoundEffect(context, R.raw.button_click)
                    onNavigate(routeValue)
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
        Spacer(Modifier.weight(1.0f)) // Pushes "Exit" to bottom
        NavigationDrawerItem(
            icon = { Icon(Icons.Filled.ExitToApp, contentDescription = "Exit to Startup") },
            label = { Text("Exit to Startup", style = MaterialTheme.typography.labelMedium) },
            selected = false,
            onClick = {
                playSoundEffect(context, R.raw.button_click)
                onNavigate(Screen.Startup.route) // Navigate to Startup
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Screen.Settings.title ?: "Settings") },
                navigationIcon = { IconButton(onClick = { playSoundEffect(context, R.raw.button_click); navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)
            )
        }
    ) { pV -> Box(Modifier.fillMaxSize().padding(pV).background(MaterialTheme.colorScheme.background), Alignment.Center) { Text("Settings Screen - Coming Soon!", fontSize = 20.sp) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navController: NavController) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Screen.About.title ?: "About") },
                navigationIcon = { IconButton(onClick = { playSoundEffect(context, R.raw.button_click); navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onPrimary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)
            )
        }
    ) { pV -> Box(Modifier.fillMaxSize().padding(pV).background(MaterialTheme.colorScheme.background), Alignment.Center) { Text("About Screen - App Version 1.0.0", fontSize = 20.sp) } }
}

@Composable
fun StudentListItem(student: Student, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(Modifier.size(60.dp).clip(CircleShape)) {
                if (student.imageUri != null) Image(rememberAsyncImagePainter(ImageRequest.Builder(LocalContext.current).data(student.imageUri).error(R.drawable.error_image).placeholder(R.drawable.placeholder_image).build()), "Pic", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Image(painterResource(R.drawable.placeholder_image), "No Pic", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(student.fullName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(2.dp))
                Text("ID: ${student.studentId}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                Text("${student.major} (${student.degreeLevel})", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
            }
            IconButton(onClick = onDelete) { Icon(painterResource(R.drawable.delete), "Delete", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

private fun resetForm(
    fName: (String) -> Unit, sId: (String) -> Unit, maj: (String) -> Unit, degLvl: (String) -> Unit,
    sImgUri: (Uri?) -> Unit, nErr: (Boolean) -> Unit, iErr: (Boolean) -> Unit, mErr: (Boolean) -> Unit
) {
    fName(""); sId(""); maj(""); degLvl("Master's"); sImgUri(null); nErr(false); iErr(false); mErr(false)
}