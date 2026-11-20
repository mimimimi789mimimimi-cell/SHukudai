# 自由学習ノート（APKのつくり方）
1. GitHubで新しいリポジトリを作り、このフォルダの中身をすべてアップロード（ブランチ名 main）
2. 「Actions」タブ → Build APK → 数分待つ
3. 完了後、ページ下の Artifacts から JiyuNote-debug-apk をダウンロード → ZIPの中のapkをスマホに送ってインストール
   （Galaxy：設定で「提供元不明のアプリ」を許可）

## テーマの追加・更新
- app/src/main/assets/themes.json に同じ形式で追加（idは重複させない）
- ネットで更新する場合：JSONをGitHubなどに置き、Data.kt の UPDATE_URL にそのURLを入れる
  （Wi-Fi接続時のみ、起動時に自動で取得。既存の記録は消えません）
- 同梱の24テーマはサンプルです。子供に見せる前に、内容を必ず確認してください
