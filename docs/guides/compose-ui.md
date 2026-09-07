# Compose Multiplatform UI Integration

[Documentation Hub](../README.md) &bull; **Guides** &bull; Compose Multiplatform UI

---

## 🎨 Declarative Web Browsing with `KromiumView`

`kromium-compose` provides a reactive composable component, `@Composable KromiumView`, engineered for seamless integration with Compose Multiplatform Desktop's rendering pipeline.

```kotlin
@Composable
fun KromiumView(
    state: KromiumViewState,
    modifier: Modifier = Modifier,
    client: KromiumClient? = null,
    loadingContent: @Composable (BoxScope.() -> Unit)? = null
)
```

---

## 🛠️ Reactive State Management (`KromiumViewState`)

State is managed via `rememberKromiumState("initialUrl")`. This state object holds observable properties that update automatically as user interactions or web page events occur:

```kotlin
val state = rememberKromiumState("https://github.com")

// Observable Properties
val currentUrl: String = state.url
val pageTitle: String = state.title
val isLoading: Boolean = state.isLoading
val canGoBack: Boolean = state.canGoBack
val canGoForward: Boolean = state.canGoForward

// Imperative Control Methods
state.loadUrl("https://kotlinlang.org")
state.goBack()
state.goForward()
state.reload()
state.stopLoading()

// Suspending JavaScript evaluation inside a Coroutine
coroutineScope.launch {
    val result: String? = state.evaluateJavaScript("document.title")
    println("Page title via JS: $result")
}
```

---

## ⏳ Custom Loading Placeholders

While the browser is establishing connection and parsing initial HTML, display a custom Compose placeholder using the `loadingContent` slot:

```kotlin
KromiumView(
    state = browserState,
    modifier = Modifier.fillMaxSize(),
    loadingContent = {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Connecting to secure server...", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
)
```

---

## 📑 Building a Multi-Tab Browser Interface

Because `KromiumViewState` is an independent `@Stable` class, implementing multi-tab browsing in Compose is as straightforward as maintaining a list of states:

```kotlin
@Composable
fun MultiTabBrowser() {
    var tabs by remember {
        mutableStateOf(
            listOf(
                KromiumViewState("https://github.com"),
                KromiumViewState("https://kotlinlang.org")
            )
        )
    }
    var selectedIndex by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        // Tab Row
        ScrollableTabRow(selectedTabIndex = selectedIndex) {
            tabs.forEachIndexed { index, tabState ->
                Tab(
                    selected = selectedIndex == index,
                    onClick = { selectedIndex = index },
                    text = {
                        val title = tabState.title.ifBlank { "New Tab" }
                        Text(title.take(20), maxLines = 1)
                    }
                )
            }
        }

        // Active Tab Browser View
        val activeState = tabs[selectedIndex]
        key(activeState) {
            KromiumView(
                state = activeState,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
```

---

## ⚡ Recomposition Safety & Memory Management

* **Lifecycle Scoping**: When `KromiumView` exits composition (such as closing a tab), it automatically detaches native Swing peers, clears handlers, and releases memory.
* **Preserving State across Recompositions**: Always use `rememberKromiumState` to avoid resetting browser history and scroll position during parent recompositions.
