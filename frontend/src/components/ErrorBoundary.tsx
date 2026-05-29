import { Component, ErrorInfo, ReactNode } from "react";

interface State {
  error: Error | null;
  info: ErrorInfo | null;
}

export class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { error: null, info: null };

  static getDerivedStateFromError(error: Error): State {
    return { error, info: null };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    this.setState({ error, info });
    console.error("[ErrorBoundary]", error, info);
  }

  render() {
    if (this.state.error) {
      return (
        <div style={{ padding: 24, fontFamily: "ui-monospace, monospace", color: "#fbb", background: "#1a0a0a", minHeight: "100vh" }}>
          <h1 style={{ color: "#ff6b6b", marginBottom: 16 }}>渲染失敗</h1>
          <pre style={{ whiteSpace: "pre-wrap", background: "#2a0d0d", padding: 16, borderRadius: 8 }}>
            <strong>{this.state.error.name}: {this.state.error.message}</strong>
            {"\n\n"}
            {this.state.error.stack}
          </pre>
          {this.state.info && (
            <pre style={{ whiteSpace: "pre-wrap", background: "#1f0a0a", padding: 16, borderRadius: 8, marginTop: 16 }}>
              {this.state.info.componentStack}
            </pre>
          )}
        </div>
      );
    }
    return this.props.children;
  }
}
